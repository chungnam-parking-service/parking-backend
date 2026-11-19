package com.chungnam.parking.feedback;

import com.chungnam.parking.common.ClientFingerprint;
import com.chungnam.parking.common.ErrorResponse;
import com.chungnam.parking.config.OpenApiConfig;
import com.chungnam.parking.feedback.FeedbackDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "정확도 피드백", description = "주차장 정보가 맞는지 사용자가 알려줍니다")
@RestController
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final ClientFingerprint fingerprint;

    @Operation(summary = "주차장 정보 정확도 피드백",
            description = "주차장 상세 화면의 \"정보가 맞아요 / 달라요\". 같은 사람은 같은 주차장에 하루 한 번만 보낼 수 있습니다.")
    @ApiResponse(responseCode = "201", description = "저장됨")
    @ApiResponse(responseCode = "404", description = "PLACE_NOT_FOUND / LOT_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "DUPLICATE_FEEDBACK (24시간 안에 이미 보냄)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/places/{placeId}/parking/{lotId}/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackResponse submit(@PathVariable String placeId, @PathVariable String lotId,
                                   @Valid @RequestBody FeedbackRequest request, HttpServletRequest http) {
        return feedbackService.submit(placeId, lotId, request, fingerprint.of(http));
    }

    @Tag(name = "관리자")
    @SecurityRequirement(name = OpenApiConfig.ADMIN_KEY)
    @Operation(summary = "피드백 집계", description = "주차장별 맞아요/달라요 수. \"달라요\"가 많은 곳부터 확인하세요.")
    @GetMapping("/api/admin/feedback")
    public List<FeedbackSummary> summary(@RequestParam(required = false) String placeId,
                                         @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return feedbackService.summary(placeId, limit);
    }

    @Tag(name = "관리자")
    @SecurityRequirement(name = OpenApiConfig.ADMIN_KEY)
    @Operation(summary = "의견이 적힌 최근 피드백")
    @GetMapping("/api/admin/feedback/comments")
    public List<FeedbackComment> comments(@RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        return feedbackService.recentComments(limit);
    }
}
