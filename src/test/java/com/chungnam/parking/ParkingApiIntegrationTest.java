package com.chungnam.parking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * data.sql 샘플 데이터(H2) 기반 API 통합 테스트
 */
@SpringBootTest
@AutoConfigureMockMvc
class ParkingApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void 지역으로_검색() throws Exception {
        mvc.perform(get("/api/parking-lots").param("region", "CHEONAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].region", everyItem(is("천안시"))));
    }

    @Test
    void 키워드와_무료_필터() throws Exception {
        mvc.perform(get("/api/parking-lots").param("keyword", "신부동").param("feeType", "FREE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("신부동 노상주차장"));
    }

    @Test
    void 내_주변_검색은_거리순() throws Exception {
        // 천안종합터미널 근처
        mvc.perform(get("/api/parking-lots/nearby")
                        .param("lat", "36.8190").param("lng", "127.1560").param("radius", "2000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("천안종합터미널 공영주차장"))
                .andExpect(jsonPath("$[0].distanceMeters", lessThan(100)))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void 충남_밖_좌표는_400() throws Exception {
        mvc.perform(get("/api/parking-lots/nearby").param("lat", "37.5665").param("lng", "126.9780"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 상세와_예상요금() throws Exception {
        mvc.perform(get("/api/parking-lots/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managementNo").value("SAMPLE-0001"))
                .andExpect(jsonPath("$.hours.weekday").value("00:00~23:59"));

        // 기본 30분 1000원 + 추가 60분(10분당 300원 x 6) = 2800원
        mvc.perform(get("/api/parking-lots/1/fee").param("minutes", "90"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedFee").value(2800));
    }

    @Test
    void 없는_주차장은_404() throws Exception {
        mvc.perform(get("/api/parking-lots/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 없는_주소는_500이_아니라_404() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 없는_정렬기준은_400() throws Exception {
        mvc.perform(get("/api/parking-lots").param("sort", "foo,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT"));
    }

    @Test
    void 주차면수_내림차순_정렬() throws Exception {
        mvc.perform(get("/api/parking-lots").param("sort", "totalSpaces,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("대천해수욕장 제1주차장"));
    }

    @Test
    void 시군_목록() throws Exception {
        mvc.perform(get("/api/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(15));
    }

    @Test
    void 스웨거_문서_생성() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("충남 주차정보 검색 API"))
                .andExpect(jsonPath("$.paths['/api/parking-lots/nearby']").exists());
    }

    @Test
    void 관리자키_없으면_동기화_401() throws Exception {
        mvc.perform(post("/api/admin/sync"))
                .andExpect(status().isUnauthorized());
    }
}
