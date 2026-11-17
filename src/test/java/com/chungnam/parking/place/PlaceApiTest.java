package com.chungnam.parking.place;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 프론트 docs/API.md 계약 테스트.
 * 기대값 JSON 은 프론트 src/mocks/data.ts 에서 그대로 추출한 것 (src/test/resources/frontend-mock).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlaceApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    PlaceRepository placeRepository;

    private static String mock(String name) throws Exception {
        return new ClassPathResource("frontend-mock/" + name + ".json").getContentAsString(StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"place-demo-cafe", "place-demo-books", "place-demo-food"})
    void 주차상세_응답이_프론트_목업과_완전히_같다(String placeId) throws Exception {
        mvc.perform(get("/places/{id}/parking", placeId))
                .andExpect(status().isOk())
                .andExpect(content().json(mock(placeId), JsonCompareMode.STRICT));
    }

    @Test
    void 검색_결과의_Place_구조가_목업과_같다() throws Exception {
        // 목업 3곳 모두 주소에 "충남" 포함. 정렬 순서는 비교하지 않음(LENIENT)
        mvc.perform(get("/places").param("query", "충남"))
                .andExpect(status().isOk())
                .andExpect(content().json(mock("all-places"), JsonCompareMode.LENIENT))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.total").value(3));
    }

    @ParameterizedTest
    @ValueSource(strings = {"커피", "모퉁이커피", " 모퉁이 커피 ", "카페"})
    void 이름_주소_카테고리로_공백무시_검색(String query) throws Exception {
        mvc.perform(get("/places").param("query", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("place-demo-cafe"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void 빈_검색은_빈_목록() throws Exception {
        mvc.perform(get("/places").param("query", "   "))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"items\":[],\"total\":0}", JsonCompareMode.STRICT));
    }

    @Test
    void 결과_없음은_200과_빈_items() throws Exception {
        mvc.perform(get("/places").param("query", "존재하지않는가게"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void 검색어_100자_초과는_400() throws Exception {
        mvc.perform(get("/places").param("query", "가".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void 없는_목적지는_404_PLACE_NOT_FOUND() throws Exception {
        mvc.perform(get("/places/{id}/parking", "place-unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("목적지를 찾을 수 없습니다."));
    }

    @Test
    @Transactional
    void 공공데이터_주변주차장이_alternatives_로_붙는다() throws Exception {
        // 천안종합터미널 근처 목적지 (data.sql 샘플 주차장 기준)
        placeRepository.save(Place.builder()
                .id("place-test-terminal").name("터미널 앞 분식").category("음식점")
                .address("충남 천안시 동남구 신부동").latitude(36.8193).longitude(127.1560)
                .parkingStatus(PlaceParkingStatus.UNKNOWN).parkingSummary("주차 정보 확인 중")
                .demo(false).includePublicNearby(true)
                .build());

        mvc.perform(get("/places/{id}/parking", "place-test-terminal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryParking").isArray())
                .andExpect(jsonPath("$.primaryParking").isEmpty())
                .andExpect(jsonPath("$.isDemo").value(false))
                // 가장 가까운 곳: 천안종합터미널 공영주차장 (SAMPLE-0001, id=1)
                .andExpect(jsonPath("$.alternatives[0].lot.id").value("lot-1"))
                .andExpect(jsonPath("$.alternatives[0].relationship").value("nearby"))
                .andExpect(jsonPath("$.alternatives[0].lot.feeDescription").value("기본 30분 1,000원 · 추가 10분당 300원 · 1일 10,000원"))
                .andExpect(jsonPath("$.alternatives[0].lot.hoursDescription").value("24시간"))
                .andExpect(jsonPath("$.alternatives[0].lot.entranceCoordinates.latitude").value(36.8195))
                // 공공데이터 좌표는 입구가 아니라 대표 좌표
                .andExpect(jsonPath("$.alternatives[*].lot.coordinateType", everyItem(is("representative"))))
                // 모르는 값은 null / [] 로 (필드 자체는 존재)
                .andExpect(jsonPath("$.alternatives[0].walkingMinutes").value(nullValue()))
                .andExpect(jsonPath("$.alternatives[0].benefitDescription").value(nullValue()))
                .andExpect(jsonPath("$.alternatives[0].lot.verifiedAt").value(nullValue()))
                .andExpect(jsonPath("$.alternatives[0].walkingSteps").isEmpty())
                // 무료 노상주차장은 "무료", 요일별 시간이 다르면 나눠서 표기
                .andExpect(jsonPath("$.alternatives[?(@.lot.id == 'lot-3')].lot.feeDescription").value(contains("무료")))
                .andExpect(jsonPath("$.alternatives[?(@.lot.id == 'lot-3')].lot.hoursDescription")
                        .value(contains("평일 09:00–20:00 · 토 09:00–18:00")))
                // 직접 등록한 시연 주차장(lot-demo-*)은 공공데이터 주변 검색에 섞이지 않음
                .andExpect(jsonPath("$.alternatives[*].lot.id", everyItem(startsWith("lot-"))))
                .andExpect(jsonPath("$.alternatives[*].lot.id", not(hasItem(startsWith("lot-demo")))));
    }

    @Test
    void 시연용_건물주차장은_일반_주차장_검색에_나오지_않는다() throws Exception {
        mvc.perform(get("/api/parking-lots").param("keyword", "예시"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void 프론트_개발서버에서_places_CORS_허용() throws Exception {
        mvc.perform(options("/places")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
