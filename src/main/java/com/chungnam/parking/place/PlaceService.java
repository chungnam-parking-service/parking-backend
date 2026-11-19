package com.chungnam.parking.place;

import com.chungnam.parking.domain.ParkingLot;
import com.chungnam.parking.place.dto.PlaceDtos.*;
import com.chungnam.parking.repository.ParkingLotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 프론트 API.md 의 /places, /places/{placeId}/parking 응답을 조립한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceService {

    public static final int MAX_QUERY_LENGTH = 100;
    public static final int MAX_SEARCH_LIMIT = 50;
    static final int NEARBY_RADIUS_METERS = 1000;
    static final int NEARBY_LIMIT = 10;
    private static final double WALKING_DETOUR_FACTOR = 1.3;
    private static final double WALKING_METERS_PER_MINUTE = 75d;
    private static final double METERS_PER_DEGREE_LAT = 111_320d;
    private static final ZoneOffset SEOUL = ZoneOffset.ofHours(9);

    private final PlaceRepository placeRepository;
    private final PlaceParkingLinkRepository linkRepository;
    private final ParkingLotRepository parkingLotRepository;
    private final PlaceCardService cardService;
    private final com.chungnam.parking.report.ReportOverlay reportOverlay;

    /**
     * GET /places?query=&limit=
     * 직접 등록한 목적지를 먼저, 이어서 AI 파트 매장 카드를 limit 개까지. total 은 전체 일치 수.
     */
    public SearchPlacesResponse search(String query, int limit) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return new SearchPlacesResponse(List.of(), 0);   // 목업과 동일: 빈 검색은 빈 목록
        }
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("검색어는 " + MAX_QUERY_LENGTH + "자 이하로 입력해 주세요.");
        }
        List<PlaceDto> own = placeRepository.search(Place.normalize(trimmed)).stream()
                .map(PlaceService::toPlaceDto)
                .toList();
        Set<String> ownIds = own.stream().map(PlaceDto::id).collect(Collectors.toSet());
        PlaceCardService.CardSearch cards = cardService.search(trimmed, limit);
        List<PlaceDto> items = java.util.stream.Stream.concat(own.stream(),
                        cards.items().stream().filter(p -> !ownIds.contains(p.id())))   // 같은 ID 면 직접 등록이 우선
                .limit(limit)
                .toList();
        return new SearchPlacesResponse(items, (int) Math.min(Integer.MAX_VALUE, own.size() + cards.total()));
    }

    /** GET /places/{placeId}/parking. 직접 등록한 목적지가 없으면 AI 파트 매장 카드에서 찾는다. */
    public PlaceParkingResponse getParking(String placeId) {
        // 승인된 사용자 제보(입구 사진·안내·요금 등)를 마지막에 덧씌운다
        return reportOverlay.apply(buildParking(placeId));
    }

    private PlaceParkingResponse buildParking(String placeId) {
        Place place = placeRepository.findById(placeId).orElse(null);
        if (place == null) {
            return cardService.find(placeId).orElseThrow(() -> new PlaceNotFoundException(placeId));
        }

        List<PlaceParkingLink> links = linkRepository.findByPlace_IdOrderBySortOrderAsc(placeId);

        List<PlaceParkingOption> primary = links.stream()
                .filter(l -> l.getRelationship().isPrimary())
                .map(this::toOption)
                .toList();

        // alternatives = 직접 등록한 주변 주차장 + (옵션) 공공데이터 주변 주차장
        List<PlaceParkingOption> alternatives = new ArrayList<>(links.stream()
                .filter(l -> !l.getRelationship().isPrimary())
                .map(this::toOption)
                .toList());

        if (place.isIncludePublicNearby()) {
            Set<Long> linkedLotIds = links.stream().map(l -> l.getParkingLot().getId()).collect(Collectors.toSet());
            publicNearby(place).stream()
                    .filter(n -> !linkedLotIds.contains(n.lot().getId()))
                    .map(n -> new PlaceParkingOption(toLotDto(n.lot()), ParkingRelationship.NEARBY.value(),
                            null, null, null, List.of(),
                            (int) Math.round(n.meters()), estimateWalkingMinutes(n.meters())))
                    .forEach(alternatives::add);
        }

        // 도보 시간 오름차순, 미확인(null)은 마지막. 정렬이 안정적이라 null 끼리는 등록 순서 → 거리 순 유지
        alternatives.sort(Comparator.comparing(PlaceParkingOption::walkingMinutes,
                Comparator.nullsLast(Comparator.naturalOrder())));

        return new PlaceParkingResponse(
                toPlaceDto(place),
                place.getHoursDescription(),
                place.getOperatingHoursWarning(),
                primary,
                alternatives,
                place.isDemo());
    }

    /** 주변 주차장과 목적지까지 직선거리(m) */
    private record NearbyLot(ParkingLot lot, double meters) {
    }

    /**
     * 직선거리로 도보 시간을 추정한다. AI 파트와 같은 식: 직선거리 × 1.3(실제 길 보정) ÷ 75m/분, 반올림, 최소 1분.
     * 실제 경로가 아니므로 walkingMinutes 가 아니라 estimatedWalkingMinutes 로만 내보낸다.
     */
    static int estimateWalkingMinutes(double meters) {
        return (int) Math.max(1, Math.round(meters * WALKING_DETOUR_FACTOR / WALKING_METERS_PER_MINUTE));
    }

    /**
     * 목적지 주차 응답에 그 주차장(lotId)이 있는지 확인한다. 피드백·제보가 화면에 보이는 주차장에만 붙도록.
     * 목적지가 없으면 404 PLACE_NOT_FOUND, 주차장이 없으면 404 LOT_NOT_FOUND.
     */
    public void requireLot(String placeId, String lotId) {
        PlaceParkingResponse parking = getParking(placeId);
        boolean found = java.util.stream.Stream.concat(parking.primaryParking().stream(), parking.alternatives().stream())
                .anyMatch(o -> o.lot().id().equals(lotId));
        if (!found) {
            throw new com.chungnam.parking.common.ApiException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "LOT_NOT_FOUND", "이 목적지에 없는 주차장입니다.");
        }
    }

    /** 목적지 좌표 기준 반경 내 공공데이터 주차장 (가까운 순) */
    private List<NearbyLot> publicNearby(Place place) {
        double lat = place.getLatitude();
        double lng = place.getLongitude();
        double dLat = NEARBY_RADIUS_METERS / METERS_PER_DEGREE_LAT;
        double dLng = NEARBY_RADIUS_METERS / (METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(lat)));

        List<ParkingLotRepository.NearbyRow> rows = parkingLotRepository.findNearby(
                lat, lng, lat - dLat, lat + dLat, lng - dLng, lng + dLng, NEARBY_RADIUS_METERS, NEARBY_LIMIT);
        Map<Long, ParkingLot> byId = parkingLotRepository.findAllById(
                        rows.stream().map(ParkingLotRepository.NearbyRow::getId).toList())
                .stream().collect(Collectors.toMap(ParkingLot::getId, Function.identity()));

        return rows.stream()
                .filter(r -> byId.containsKey(r.getId()))
                .map(r -> new NearbyLot(byId.get(r.getId()), r.getDistance()))
                .filter(n -> n.lot().getLatitude() != null && n.lot().getLongitude() != null) // 입구 좌표 필수
                .toList();
    }

    private PlaceParkingOption toOption(PlaceParkingLink link) {
        return new PlaceParkingOption(
                toLotDto(link.getParkingLot()),
                link.getRelationship().value(),
                link.getBenefitDescription(),
                link.getValidationInstructions(),
                link.getWalkingMinutes(),
                List.copyOf(link.getWalkingSteps()));
    }

    static PlaceDto toPlaceDto(Place p) {
        return new PlaceDto(p.getId(), p.getName(), p.getCategory(), p.getAddress(),
                new Coordinates(p.getLatitude(), p.getLongitude()),
                p.getParkingStatus().value(), p.getParkingSummary());
    }

    static ParkingLotDto toLotDto(ParkingLot lot) {
        // 직접 등록 주차장은 등록한 외부 ID, 공공데이터 주차장은 "lot-{번호}"
        String id = lot.isManual() ? lot.getManagementNo() : "lot-" + lot.getId();

        boolean hasEntrance = lot.getEntranceLatitude() != null && lot.getEntranceLongitude() != null;
        Coordinates entrance = hasEntrance
                ? new Coordinates(lot.getEntranceLatitude(), lot.getEntranceLongitude())
                : new Coordinates(lot.getLatitude(), lot.getLongitude()); // 공공데이터: 대표 좌표
        String coordinateType = hasEntrance ? "entrance" : "representative";

        String fee = lot.getFeeDescription() != null ? lot.getFeeDescription()
                : lot.isManual() ? null : LotDescriptions.fee(lot.getFeeType(), lot.getFeeInfo());
        String hours = lot.getHoursDescription() != null ? lot.getHoursDescription()
                : lot.isManual() ? null : LotDescriptions.hours(lot.getOperatingHours());

        return new ParkingLotDto(
                id,
                lot.getName(),
                lot.getDisplayAddress(),
                entrance,
                lot.getEntrancePhotoUrl(),
                lot.getEntranceGuide(),
                fee,
                hours,
                lot.getVerifiedAt() == null ? null : lot.getVerifiedAt().atOffset(SEOUL),
                coordinateType);
    }
}
