package com.chungnam.parking.assistant;

import com.chungnam.parking.assistant.AssistantDtos.ChatRequest;
import com.chungnam.parking.assistant.AssistantDtos.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * AI 파트 주차 비서 서버(AI 레포 server/app.py) 호출.
 * LLM 응답이 4–16초 걸리므로 읽기 타임아웃을 넉넉히 둔다.
 */
@Slf4j
@Component
public class AssistantClient {

    private final RestClient restClient;
    private final String baseUrl;

    public AssistantClient(RestClient.Builder builder,
                           @Value("${ai-assistant.url:}") String baseUrl,
                           @Value("${ai-assistant.read-timeout-ms:60000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(readTimeoutMs);
        this.restClient = builder.requestFactory(factory).build();
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    public boolean isConfigured() {
        return !baseUrl.isBlank();
    }

    public ChatResponse chat(ChatRequest request) {
        if (!isConfigured()) {
            throw new AssistantUnavailableException("ASSISTANT_UNAVAILABLE", "주차 비서가 설정되지 않았습니다.");
        }
        try {
            ChatResponse response = restClient.post()
                    .uri(baseUrl + "/assistant/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
            if (response == null || response.answer() == null) {
                throw new AssistantUnavailableException("ASSISTANT_FAILED", "주차 비서 응답이 비어 있습니다.");
            }
            return response;
        } catch (RestClientException e) {
            log.warn("AI 주차 비서 호출 실패: {}", e.getMessage());
            throw new AssistantUnavailableException("ASSISTANT_FAILED",
                    "주차 비서 응답에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }
}
