package com.chungnam.parking.place;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "admin.api-key=test-key")
@AutoConfigureMockMvc
@Transactional
class PlaceAdminApiTest {

    private static final String URL = "/api/admin/places/{id}";
    private static final String ID = "place-admin-test";

    @Autowired
    MockMvc mvc;

    /** 직접 등록 주차장 1개 + 공공데이터(lot-1) 연결 1개를 가진 정상 요청 */
    private static String body(String name, String parking) {
        return """
                {"name":"%s","category":"카페","address":"충남 천안시 서북구 불당동 1",
                 "latitude":36.815,"longitude":127.1085,
                 "parkingStatus":"available","parkingSummary":"건물 주차 가능",
                 "includePublicNearby":false,"parking":%s}
                """.formatted(name, parking);
    }

    private static final String TWO_LOTS = """
            [{"relationship":"shared",
              "lot":{"id":"lot-admin-test","name":"테스트 빌딩 주차장","address":"충남 천안시 서북구 불당동 1",
                     "latitude":36.8148,"longitude":127.1082,"feeDescription":"1시간 무료",
                     "verifiedAt":"2026-11-18T09:00:00+09:00"},
              "benefitDescription":"2시간 무료","walkingMinutes":2,"walkingSteps":["엘리베이터 이용","정문으로 나오세요"]},
             {"relationship":"nearby","existingLotId":"lot-1","walkingMinutes":6}]
            """;

    private ResultActions upsert(String id, String json) throws Exception {
        return mvc.perform(put(URL, id).header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void 키가_없거나_틀리면_401() throws Exception {
        mvc.perform(put(URL, ID).contentType(MediaType.APPLICATION_JSON).content(body("x", "[]")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(delete(URL, ID).header("X-Admin-Key", "wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/sync"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 등록하면_응답과_조회_API에_그대로_반영된다() throws Exception {
        upsert(ID, body("관리자 카페", TWO_LOTS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.place.id").value(ID))
                .andExpect(jsonPath("$.place.parkingStatus").value("available"))
                .andExpect(jsonPath("$.isDemo").value(false))
                .andExpect(jsonPath("$.primaryParking.length()").value(1))
                .andExpect(jsonPath("$.primaryParking[0].lot.id").value("lot-admin-test"))
                .andExpect(jsonPath("$.primaryParking[0].lot.verifiedAt").value("2026-11-18T09:00:00+09:00"))
                .andExpect(jsonPath("$.primaryParking[0].walkingSteps.length()").value(2))
                .andExpect(jsonPath("$.primaryParking[0].lot.coordinateType").value("entrance"))
                .andExpect(jsonPath("$.alternatives[0].lot.id").value("lot-1"))
                .andExpect(jsonPath("$.alternatives[0].lot.coordinateType").value("representative"))
                .andExpect(jsonPath("$.alternatives[0].walkingMinutes").value(6));

        mvc.perform(get("/places/{id}/parking", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryParking[0].benefitDescription").value("2시간 무료"));
        mvc.perform(get("/places").param("query", "관리자카페"))
                .andExpect(jsonPath("$.items[0].id").value(ID));
    }

    @Test
    void 같은_ID로_다시_보내면_통째로_교체된다() throws Exception {
        upsert(ID, body("처음 이름", TWO_LOTS)).andExpect(status().isOk());
        upsert(ID, body("바뀐 이름", "[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.place.name").value("바뀐 이름"))
                .andExpect(jsonPath("$.primaryParking").isEmpty())
                .andExpect(jsonPath("$.alternatives").isEmpty());
        mvc.perform(get("/places").param("query", "처음이름"))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void 시연_목적지를_덮어쓰면_isDemo가_꺼진다() throws Exception {
        upsert("place-demo-food", body("진짜 식당", "[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDemo").value(false));
    }

    @Test
    void 삭제하면_204_이후_404() throws Exception {
        upsert(ID, body("지울 곳", TWO_LOTS)).andExpect(status().isOk());
        mvc.perform(delete(URL, ID).header("X-Admin-Key", "test-key")).andExpect(status().isNoContent());
        mvc.perform(get("/places/{id}/parking", ID)).andExpect(status().isNotFound());
        mvc.perform(delete(URL, ID).header("X-Admin-Key", "test-key"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // 좌표가 충남 밖
            "{\"name\":\"a\",\"address\":\"충남 천안시\",\"latitude\":37.5,\"longitude\":127.1,\"parkingStatus\":\"unknown\",\"parkingSummary\":\"s\"}",
            // 허용되지 않는 parkingStatus
            "{\"name\":\"a\",\"address\":\"충남 천안시\",\"latitude\":36.8,\"longitude\":127.1,\"parkingStatus\":\"maybe\",\"parkingSummary\":\"s\"}",
            // 필수값(name) 없음
            "{\"address\":\"충남 천안시\",\"latitude\":36.8,\"longitude\":127.1,\"parkingStatus\":\"unknown\",\"parkingSummary\":\"s\"}",
            // 깨진 JSON
            "{\"name\":"
    })
    void 잘못된_본문은_400(String json) throws Exception {
        upsert(ID, json).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // lot 과 existingLotId 를 동시에
            "[{\"relationship\":\"nearby\",\"existingLotId\":\"lot-1\",\"lot\":{\"id\":\"a\",\"name\":\"n\",\"address\":\"충남 천안시\",\"latitude\":36.8,\"longitude\":127.1}}]",
            // 둘 다 없음
            "[{\"relationship\":\"nearby\"}]",
            // 없는 공공데이터 주차장
            "[{\"relationship\":\"nearby\",\"existingLotId\":\"lot-99999999\"}]",
            // 공공데이터 형식과 같은 직접 등록 id
            "[{\"relationship\":\"shared\",\"lot\":{\"id\":\"lot-5\",\"name\":\"n\",\"address\":\"충남 천안시\",\"latitude\":36.8,\"longitude\":127.1}}]",
            // 주소에서 충남 시·군을 못 찾음
            "[{\"relationship\":\"shared\",\"lot\":{\"id\":\"ok-id\",\"name\":\"n\",\"address\":\"서울시 강남구\",\"latitude\":36.8,\"longitude\":127.1}}]",
            // 허용되지 않는 relationship
            "[{\"relationship\":\"friend\",\"existingLotId\":\"lot-1\"}]"
    })
    void 잘못된_주차장_연결은_400(String parking) throws Exception {
        upsert(ID, body("실패", parking)).andExpect(status().isBadRequest());
    }

    @Test
    void 잘못된_placeId는_400() throws Exception {
        upsert("-bad", body("x", "[]")).andExpect(status().isBadRequest());
    }

    @Test
    void AI파트_데이터_형식의_대문자_ID도_등록된다() throws Exception {
        // AI 레포 샘플: 매장 sangga-MA010120220808573659, 주차장 osm-way-120027457
        String lot = """
                [{"relationship":"nearby","lot":{"id":"osm-way-120027457","name":"이름 없는 주차장",
                  "address":"충남 천안시 서북구 직산읍","latitude":36.881993,"longitude":127.116174},
                  "walkingMinutes":8}]
                """;
        upsert("sangga-MA010120220808573659", body("배나무숲", lot))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.place.id").value("sangga-MA010120220808573659"))
                .andExpect(jsonPath("$.alternatives[0].lot.id").value("osm-way-120027457"));
    }
}
