package com.chungnam.parking.feedback;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 정확도 피드백: 시연 목적지(place-demo-cafe)의 연결 주차장(lot-demo-cafe)으로 확인 */
@SpringBootTest(properties = "admin.api-key=test-admin-key")
@AutoConfigureMockMvc
@Transactional
class FeedbackApiTest {

    static final String URL = "/places/{placeId}/parking/{lotId}/feedback";

    @Autowired
    MockMvc mvc;

    private MockHttpServletRequestBuilder feedback(String placeId, String lotId, String body, String ip) {
        return post(URL, placeId, lotId).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Forwarded-For", ip);
    }

    @Test
    void 피드백을_저장한다() throws Exception {
        mvc.perform(feedback("place-demo-cafe", "lot-demo-cafe",
                        "{\"accurate\": false, \"comment\": \"입구가 건물 뒤편이에요\"}", "10.0.0.1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.placeId").value("place-demo-cafe"))
                .andExpect(jsonPath("$.lotId").value("lot-demo-cafe"))
                .andExpect(jsonPath("$.accurate").value(false))
                .andExpect(jsonPath("$.createdAt", endsWith("+09:00")));
    }

    @Test
    void 같은_사람은_하루에_한_번만() throws Exception {
        String body = "{\"accurate\": true}";
        mvc.perform(feedback("place-demo-cafe", "lot-demo-cafe", body, "10.0.0.2")).andExpect(status().isCreated());
        mvc.perform(feedback("place-demo-cafe", "lot-demo-cafe", body, "10.0.0.2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_FEEDBACK"));
        // 다른 사람, 같은 사람의 다른 주차장은 가능
        mvc.perform(feedback("place-demo-cafe", "lot-demo-cafe", body, "10.0.0.3")).andExpect(status().isCreated());
        mvc.perform(feedback("place-demo-cafe", "lot-demo-public", body, "10.0.0.2")).andExpect(status().isCreated());
    }

    @Test
    void 목적지에_없는_주차장과_없는_목적지는_404() throws Exception {
        mvc.perform(feedback("place-demo-cafe", "lot-demo-onyang", "{\"accurate\": true}", "10.0.0.4"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOT_NOT_FOUND"));
        mvc.perform(feedback("place-unknown", "lot-demo-cafe", "{\"accurate\": true}", "10.0.0.4"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    @Test
    void accurate_가_없거나_의견이_너무_길면_400() throws Exception {
        mvc.perform(feedback("place-demo-cafe", "lot-demo-cafe", "{}", "10.0.0.5"))
                .andExpect(status().isBadRequest());
        mvc.perform(feedback("place-demo-cafe", "lot-demo-cafe",
                        "{\"accurate\": true, \"comment\": \"" + "가".repeat(301) + "\"}", "10.0.0.5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 관리자는_주차장별_집계를_본다() throws Exception {
        mvc.perform(feedback("place-demo-cafe", "lot-demo-cafe", "{\"accurate\": false, \"comment\": \"뒤편\"}", "10.0.1.1"));
        mvc.perform(feedback("place-demo-cafe", "lot-demo-cafe", "{\"accurate\": false}", "10.0.1.2"));
        mvc.perform(feedback("place-demo-cafe", "lot-demo-cafe", "{\"accurate\": true}", "10.0.1.3"));

        mvc.perform(get("/api/admin/feedback").param("placeId", "place-demo-cafe"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/feedback").param("placeId", "place-demo-cafe").header("X-Admin-Key", "test-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lotId").value("lot-demo-cafe"))
                .andExpect(jsonPath("$[0].accurate").value(1))
                .andExpect(jsonPath("$[0].inaccurate").value(2))
                .andExpect(jsonPath("$[0].total").value(3));
        mvc.perform(get("/api/admin/feedback/comments").header("X-Admin-Key", "test-admin-key"))
                .andExpect(jsonPath("$[*].comment", hasItem("뒤편")));
    }

    @Test
    void 프론트_개발서버에서_POST_CORS_허용() throws Exception {
        mvc.perform(options(URL, "place-demo-cafe", "lot-demo-cafe")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
