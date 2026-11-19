package com.chungnam.parking.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** AI_ASSISTANT_URL 이 비어 있으면 503 ASSISTANT_UNAVAILABLE */
@SpringBootTest(properties = "ai-assistant.url=")
@AutoConfigureMockMvc
class AssistantNotConfiguredTest {

    @Autowired
    MockMvc mvc;

    @Test
    void 설정이_없으면_503() throws Exception {
        mvc.perform(post("/assistant/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"배나무숲\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ASSISTANT_UNAVAILABLE"));
    }
}
