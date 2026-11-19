package com.chungnam.parking.feedback;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public final class FeedbackDtos {

    private FeedbackDtos() {
    }

    public record FeedbackRequest(
            @Schema(description = "true = 정보가 맞아요, false = 달라요", example = "true")
            @NotNull Boolean accurate,
            @Schema(description = "선택. 무엇이 달랐는지 (300자 이하)", example = "입구가 건물 뒤편이에요")
            @Size(max = 300) String comment) {
    }

    public record FeedbackResponse(Long id, String placeId, String lotId, boolean accurate, OffsetDateTime createdAt) {
    }

    /** 관리자용 주차장별 집계 */
    public record FeedbackSummary(String placeId, String lotId, long accurate, long inaccurate, long total,
                                  OffsetDateTime lastAt) {
    }

    /** 관리자용 의견이 있는 피드백 */
    public record FeedbackComment(Long id, String placeId, String lotId, boolean accurate, String comment,
                                  OffsetDateTime createdAt) {
    }
}
