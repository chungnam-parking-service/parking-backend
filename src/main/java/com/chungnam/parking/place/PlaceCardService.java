package com.chungnam.parking.place;

import com.chungnam.parking.domain.ChungnamRegion;
import com.chungnam.parking.place.dto.PlaceDtos.PlaceDto;
import com.chungnam.parking.place.dto.PlaceDtos.PlaceParkingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * AI 파트 매장 주차 카드(place_parking_cn.jsonl) 가져오기·검색·조회.
 *
 * 가져올 때 프론트 계약(src/domain/parking.ts)에 맞게 변환한다.
 * - 입구 좌표가 없는 건물 주차장: 매장 좌표를 넣고 coordinateType=representative
 * - 입구 좌표 출처가 "OSM 주차장 입구 표시"일 때만 coordinateType=entrance (추정·미확인은 representative)
 * - 주소 null → "" / verifiedAt → null (공공데이터 기준일은 팀이 확인한 시각이 아니다)
 * - 직선거리로 추정한 도보 시간 → walkingMinutes 는 null (등록값만 쓴다는 계약), 추정값은 estimatedWalkingMinutes 로
 * - 직선거리 distanceMeters 는 그대로 (v1.2 선택 필드)
 * 계약 형식으로 읽히지 않는 행은 건너뛰고 개수만 센다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceCardService {

    /** "천안시" → "천안" (질의·주소에서 시·군을 찾는 데 쓴다) */
    private static final List<String> REGIONS = Arrays.stream(ChungnamRegion.values())
            .map(r -> r.getLabel().substring(0, r.getLabel().length() - 1))
            .toList();
    private static final int BATCH = 1000;
    private static final String INSERT =
            "insert into place_card (id, name_key, category_key, address, status_rank, payload) values (?, ?, ?, ?, ?, ?)";

    private final PlaceCardRepository repository;
    private final JdbcTemplate jdbc;
    private final JsonMapper mapper;

    @Value("${ai-cards.url:}")
    private String sourceUrl;

    public record ImportResult(int imported, int skipped, long elapsedMs) {
    }

    public record CardSearch(long total, List<PlaceDto> items) {
    }

    /** 설정된 ai-cards.url 에서 가져온다 */
    @Transactional
    public ImportResult importCards() {
        if (sourceUrl.isBlank()) {
            throw new IllegalStateException("AI 데이터 주소(AI_CARDS_URL)가 설정되지 않았습니다.");
        }
        return importFrom(sourceUrl);
    }

    /** url: https 또는 file 주소. .gz 면 압축을 풀며 읽는다. 기존 카드는 모두 지우고 새로 넣는다(실패하면 롤백). */
    @Transactional
    public ImportResult importFrom(String url) {
        long started = System.currentTimeMillis();
        ObjectReader reader = mapper.readerFor(PlaceParkingResponse.class)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        int imported = 0;
        int skipped = 0;
        List<Object[]> batch = new ArrayList<>(BATCH);

        jdbc.update("delete from place_card");
        try (InputStream raw = URI.create(url).toURL().openStream();
             BufferedReader in = new BufferedReader(new InputStreamReader(
                     url.endsWith(".gz") ? new GZIPInputStream(raw) : raw, StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                PlaceParkingResponse card;
                try {
                    card = reader.readValue(normalize(mapper.readTree(line)));
                } catch (RuntimeException e) {   // 계약 형식으로 읽히지 않는 행
                    skipped++;
                    continue;
                }
                PlaceDto p = card.place();
                batch.add(new Object[]{p.id(), key(p.name()), key(p.category()), p.address(),
                        statusRank(p.parkingStatus()), mapper.writeValueAsString(card)});
                if (batch.size() == BATCH) {
                    imported += flush(batch);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("AI 데이터를 읽지 못했습니다: " + e.getMessage(), e);
        }
        imported += flush(batch);

        ImportResult result = new ImportResult(imported, skipped, System.currentTimeMillis() - started);
        log.info("[AI 카드] {}", result);
        return result;
    }

    /** GET /places 용: "천안 빙빙반점"이면 주소에 천안이 있는 매장 중 이름에 빙빙반점이 있는 곳 */
    public CardSearch search(String query, int limit) {
        String region = REGIONS.stream().filter(query::contains).findFirst().orElse(null);
        String rest = region == null ? query
                : query.replace(region + "시", "").replace(region + "군", "").replace(region, "");
        String q = key(rest);
        if (region == null && q.isEmpty()) {
            return new CardSearch(0, List.of());
        }
        long total = repository.countSearch(region, q);
        List<PlaceDto> items = total == 0 ? List.of()
                : repository.search(region, q, Limit.of(limit)).stream().map(c -> read(c).place()).toList();
        return new CardSearch(total, items);
    }

    public Optional<PlaceParkingResponse> find(String placeId) {
        return repository.findById(placeId).map(this::read);
    }

    private PlaceParkingResponse read(PlaceCard card) {
        return mapper.readValue(card.getPayload(), PlaceParkingResponse.class);
    }

    /** AI 원본 한 줄을 프론트 계약에 맞게 고친다 (클래스 설명 참고) */
    static JsonNode normalize(JsonNode row) {
        JsonNode placeCoordinates = row.path("place").path("coordinates");
        for (String group : List.of("primaryParking", "alternatives")) {
            for (JsonNode option : row.path(group)) {
                ObjectNode o = (ObjectNode) option;
                ObjectNode lot = (ObjectNode) o.get("lot");
                String source = lot.path("entranceSource").isString() ? lot.get("entranceSource").stringValue() : "";
                boolean hasEntrance = lot.path("entranceCoordinates").isObject();
                if (!hasEntrance) {
                    lot.set("entranceCoordinates", placeCoordinates.deepCopy());
                }
                boolean confirmed = hasEntrance && source.contains("입구 표시");
                lot.put("coordinateType", confirmed ? "entrance" : "representative");
                if (!lot.path("address").isString()) {
                    lot.put("address", "");
                }
                lot.putNull("verifiedAt");
                // 직선거리 추정 도보 시간은 walkingMinutes(확인된 값)에서 빼고 estimatedWalkingMinutes 로 옮긴다
                if (o.path("walkingMinutesIsEstimate").asBoolean(false)) {
                    JsonNode minutes = o.get("walkingMinutes");
                    if (minutes != null && minutes.isNumber() && minutes.asInt() >= 0) {
                        o.put("estimatedWalkingMinutes", minutes.asInt());
                    }
                    o.putNull("walkingMinutes");
                }
                // 직선거리(m)는 그대로 내보낸다. 숫자가 아니면 버린다
                if (o.has("distanceMeters") && !(o.get("distanceMeters").isNumber() && o.get("distanceMeters").asInt() >= 0)) {
                    o.remove("distanceMeters");
                }
            }
        }
        return row;
    }

    /** 검색 키: 한글·영문·숫자만 남기고 소문자 (LIKE 와일드카드도 함께 제거된다) */
    static String key(String text) {
        return text == null ? "" : text.replaceAll("[^0-9A-Za-z가-힣]", "").toLowerCase();
    }

    private static int statusRank(String parkingStatus) {
        return switch (parkingStatus) {
            case "available" -> 0;
            case "unavailable" -> 1;
            default -> 2;
        };
    }

    private int flush(List<Object[]> batch) {
        if (batch.isEmpty()) {
            return 0;
        }
        jdbc.batchUpdate(INSERT, batch);
        int n = batch.size();
        batch.clear();
        return n;
    }
}
