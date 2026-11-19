package com.chungnam.parking.place;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AI 파트 매장 카드: 가져오기 + 프론트 계약 변환 + 검색·상세.
 * 입력은 AI 레포 samples/place_parking_sample.jsonl 원본 그대로(화면 경우의 수별 8곳).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlaceCardApiTest {

    private static final String CAFE = "sangga-MA010120220808573659";        // 배나무숲: 건물 주차장 입구 좌표 없음
    private static final String WITH_ENTRANCE = "sangga-MA010120220803118414"; // 대체 주차장에 OSM 입구 표시

    @Autowired
    MockMvc mvc;

    @Autowired
    PlaceCardService cardService;

    @BeforeEach
    void importSample() throws Exception {
        String url = new ClassPathResource("ai-cards-sample.jsonl").getURL().toString();
        PlaceCardService.ImportResult result = cardService.importFrom(url);
        assertThat(result.imported()).isEqualTo(8);
        assertThat(result.skipped()).isZero();
    }

    @Test
    void 매장명으로_검색된다() throws Exception {
        mvc.perform(get("/places").param("query", "배나무숲"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(CAFE))
                .andExpect(jsonPath("$.items[0].coordinates.latitude").isNumber());
    }

    @Test
    void 시군_이름은_주소_조건이고_limit_만큼만_준다() throws Exception {
        mvc.perform(get("/places").param("query", "천안").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.total", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.items[*].address", everyItem(containsString("천안"))))
                // 지역만 검색하면 주차 정보가 확인된 곳(available)이 먼저
                .andExpect(jsonPath("$.items[0].parkingStatus").value("available"));

        mvc.perform(get("/places").param("query", "천안 배나무숲"))
                .andExpect(jsonPath("$.items[0].id").value(CAFE));
        mvc.perform(get("/places").param("query", "아산 배나무숲"))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void limit_범위_밖은_400() throws Exception {
        mvc.perform(get("/places").param("query", "천안").param("limit", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 상세는_프론트_계약_형식으로_변환된다() throws Exception {
        mvc.perform(get("/places/{id}/parking", CAFE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDemo").value(false))
                // 입구 좌표가 없던 건물 주차장: 매장 좌표 + 대표 좌표 표시
                .andExpect(jsonPath("$.primaryParking[0].lot.entranceCoordinates.latitude").value(36.8795804394678))
                .andExpect(jsonPath("$.primaryParking[0].lot.coordinateType").value("representative"))
                // 날짜만 있던 verifiedAt 은 null, 주소가 없던 OSM 주차장은 ""
                .andExpect(jsonPath("$.alternatives[*].lot.verifiedAt", everyItem(nullValue())))
                .andExpect(jsonPath("$.alternatives[0].lot.address").value(""))
                // 직선거리 추정 도보 시간은 walkingMinutes(확인된 값)로 내보내지 않는다
                .andExpect(jsonPath("$.alternatives[*].walkingMinutes", everyItem(nullValue())))
                // AI 전용 필드는 응답에 섞이지 않는다
                .andExpect(jsonPath("$.alternatives[0].lot.entranceSource").doesNotExist())
                .andExpect(jsonPath("$.alternatives[0].walkingMinutesIsEstimate").doesNotExist())
                .andExpect(jsonPath("$.primaryParking[0].evidence").doesNotExist());
    }

    @Test
    void 도보_거리와_추정_시간은_선택_필드로_준다() throws Exception {
        mvc.perform(get("/places/{id}/parking", CAFE))
                .andExpect(status().isOk())
                // 디자인의 "도보 거리 약 474m · 약 8분"
                .andExpect(jsonPath("$.alternatives[0].lot.id").value("osm-way-120027457"))
                .andExpect(jsonPath("$.alternatives[0].distanceMeters").value(474))
                .andExpect(jsonPath("$.alternatives[0].estimatedWalkingMinutes").value(8))
                .andExpect(jsonPath("$.alternatives[1].distanceMeters").value(606))
                .andExpect(jsonPath("$.alternatives[1].estimatedWalkingMinutes").value(11))
                // 거리를 모르는 매장 주차장은 필드 자체가 없다 (null 로 보내지 않음)
                .andExpect(jsonPath("$.primaryParking[0].distanceMeters").doesNotExist())
                .andExpect(jsonPath("$.primaryParking[0].estimatedWalkingMinutes").doesNotExist());
    }

    @Test
    void OSM_입구_표시가_있는_주차장만_entrance() throws Exception {
        // 원본 출처: 건물 주차장 "진출입로 교차점(추정)", osm-way-472630487·472630480 "OSM 주차장 입구 표시",
        // osm-way-545771856·hol-8645 "주차장 대표 좌표(입구 미확인)"
        mvc.perform(get("/places/{id}/parking", WITH_ENTRANCE))
                .andExpect(jsonPath("$.primaryParking[0].lot.coordinateType").value("representative"))
                .andExpect(jsonPath("$.alternatives[?(@.lot.coordinateType == 'entrance')].lot.id",
                        containsInAnyOrder("osm-way-472630487", "osm-way-472630480")))
                .andExpect(jsonPath("$.alternatives[?(@.lot.coordinateType == 'representative')].lot.id",
                        containsInAnyOrder("osm-way-545771856", "hol-8645")));
    }

    @Test
    void 직접_등록한_시연_목적지와_404는_그대로() throws Exception {
        mvc.perform(get("/places/{id}/parking", "place-demo-cafe"))
                .andExpect(jsonPath("$.isDemo").value(true));
        mvc.perform(get("/places/{id}/parking", "sangga-없는매장"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    @Test
    void 다시_가져오면_통째로_교체되고_읽기_실패는_롤백된다() {
        assertThat(cardService.importFrom(urlOf("ai-cards-sample.jsonl")).imported()).isEqualTo(8);
        assertThatThrownBy(() -> cardService.importFrom("file:///없는/경로/cards.jsonl"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 가져오기_API는_관리자_키가_필요하다() throws Exception {
        mvc.perform(post("/api/admin/places/import-cards"))
                .andExpect(status().isUnauthorized());
    }

    private static String urlOf(String resource) {
        try {
            return new ClassPathResource(resource).getURL().toString();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
