package com.chungnam.parking.assistant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** AI 레포 server/app.py 의 POST /assistant/chat 과 같은 모양 */
public final class AssistantDtos {

    private AssistantDtos() {
    }

    public record Turn(
            @Schema(description = "user 또는 assistant", example = "user")
            @NotBlank @Pattern(regexp = "user|assistant") String role,
            @Schema(description = "이전 대화 내용")
            @NotBlank @Size(max = 4000) String content) {
    }

    public record ChatRequest(
            @Schema(description = "사용자 질문 (1–500자)", example = "천안 직산 배나무숲 주차 돼요?")
            @NotBlank @Size(max = 500) String message,
            @Schema(description = "이전 대화(최대 10개). 없으면 생략")
            @Size(max = 10) List<@Valid Turn> history) {

        public ChatRequest {
            history = history == null ? List.of() : history;
        }
    }

    public record ChatResponse(
            @Schema(description = "비서 답변(마크다운 일부 포함 가능)") String answer,
            @Schema(description = "비서가 호출한 도구 이름·인자(디버그용)") List<Map<String, Object>> toolCalls,
            @Schema(description = "답한 LLM 모델 이름") String model) {
    }
}
