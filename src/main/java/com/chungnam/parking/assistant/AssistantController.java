package com.chungnam.parking.assistant;

import com.chungnam.parking.assistant.AssistantDtos.ChatRequest;
import com.chungnam.parking.assistant.AssistantDtos.ChatResponse;
import com.chungnam.parking.common.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 주차 비서 중계. 프론트는 이 서버만 부르고, 실제 답은 AI 파트 서버(AI_ASSISTANT_URL)가 만든다.
 * 경로는 /places 와 같이 /api 접두사 없음.
 */
@Tag(name = "주차 비서 (AI 중계)", description = "자연어 주차 질문을 AI 파트 서버로 넘겨 답을 돌려줍니다")
@RestController
@RequestMapping("/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantClient assistantClient;

    @Operation(summary = "주차 비서에게 묻기",
            description = "예: \"천안 직산 배나무숲 주차 돼요?\". 응답까지 4–16초 걸릴 수 있습니다. "
                    + "AI 서버가 설정되지 않았거나 실패하면 503.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "400", description = "BAD_REQUEST (빈 질문, 500자 초과 등)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "ASSISTANT_UNAVAILABLE / ASSISTANT_FAILED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return assistantClient.chat(request);
    }
}
