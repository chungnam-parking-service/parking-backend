package com.chungnam.parking.assistant;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 주차 비서 중계: 가짜 AI 서버(JDK HttpServer)를 띄워 실제 HTTP 로 넘기는지 확인한다.
 */
@SpringBootTest(properties = "ai-assistant.read-timeout-ms=2000")
@AutoConfigureMockMvc
class AssistantRelayTest {

    private static final HttpServer FAKE_AI;
    private static final AtomicInteger STATUS = new AtomicInteger(200);
    private static final AtomicReference<String> BODY = new AtomicReference<>();
    private static final AtomicReference<String> RECEIVED = new AtomicReference<>();
    private static final AtomicInteger DELAY_MS = new AtomicInteger(0);

    static {
        try {
            FAKE_AI = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        FAKE_AI.createContext("/assistant/chat", exchange -> {
            RECEIVED.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(DELAY_MS.get());
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] out = BODY.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(STATUS.get(), out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        FAKE_AI.start();
    }

    @DynamicPropertySource
    static void aiUrl(DynamicPropertyRegistry registry) {
        registry.add("ai-assistant.url", () -> "http://127.0.0.1:" + FAKE_AI.getAddress().getPort() + "/");
    }

    @AfterAll
    static void stop() {
        FAKE_AI.stop(0);
    }

    @Autowired
    MockMvc mvc;

    @BeforeEach
    void reset() {
        STATUS.set(200);
        DELAY_MS.set(0);
        RECEIVED.set(null);
        BODY.set("""
                {"answer":"배나무숲은 매장 주차가 무료입니다.",
                 "toolCalls":[{"name":"search_places","args":{"query":"배나무숲"}}],
                 "model":"fake-model:free"}""");
    }

    @Test
    void 질문을_AI_서버로_넘기고_답을_그대로_돌려준다() throws Exception {
        mvc.perform(post("/assistant/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"천안 직산 배나무숲 주차 돼요?",
                                 "history":[{"role":"user","content":"안녕"},{"role":"assistant","content":"네"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("배나무숲은 매장 주차가 무료입니다."))
                .andExpect(jsonPath("$.toolCalls[0].name").value("search_places"))
                .andExpect(jsonPath("$.model").value("fake-model:free"));
        assertThat(RECEIVED.get()).contains("천안 직산 배나무숲 주차 돼요?").contains("\"role\":\"assistant\"");
    }

    @Test
    void history_없이도_된다() throws Exception {
        mvc.perform(post("/assistant/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"배나무숲\"}"))
                .andExpect(status().isOk());
        assertThat(RECEIVED.get()).contains("\"history\":[]");
    }

    @Test
    void 빈_질문과_너무_긴_질문은_400_이고_AI_서버를_부르지_않는다() throws Exception {
        mvc.perform(post("/assistant/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/assistant/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + "가".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/assistant/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"x\",\"history\":[{\"role\":\"system\",\"content\":\"y\"}]}"))
                .andExpect(status().isBadRequest());
        assertThat(RECEIVED.get()).isNull();
    }

    @Test
    void AI_서버_오류는_503_ASSISTANT_FAILED() throws Exception {
        STATUS.set(503);
        BODY.set("{\"code\":\"ASSISTANT_FAILED\",\"message\":\"x\"}");
        mvc.perform(post("/assistant/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"배나무숲\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ASSISTANT_FAILED"));
    }

    @Test
    void AI_서버가_느리면_타임아웃_후_503() throws Exception {
        DELAY_MS.set(3000);
        mvc.perform(post("/assistant/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"배나무숲\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ASSISTANT_FAILED"));
    }

    @Test
    void 프론트_출처의_CORS_사전요청을_허용한다() throws Exception {
        mvc.perform(options("/assistant/chat")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
