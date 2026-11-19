package com.chungnam.parking.report;

import com.chungnam.parking.common.ClientFingerprint;
import com.chungnam.parking.common.ErrorResponse;
import com.chungnam.parking.config.OpenApiConfig;
import com.chungnam.parking.report.ReportDtos.*;
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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "주차 정보 제보", description = "사용자가 주차 정보 등록·수정을 요청합니다. 관리자가 승인하면 화면에 반영됩니다")
@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ClientFingerprint fingerprint;

    @Operation(summary = "주차 정보 제보 (사진 없음)",
            description = "JSON 본문. 모르는 칸은 비워 두세요. 승인 전에는 화면에 나타나지 않습니다. 한 사람당 하루 10건.")
    @ApiResponse(responseCode = "201", description = "접수됨 (status = pending)")
    @ApiResponse(responseCode = "400", description = "BAD_REQUEST / EMPTY_REPORT",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "PLACE_NOT_FOUND / LOT_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "TOO_MANY_REPORTS",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping(path = "/places/{placeId}/reports", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ReportReceipt submitJson(@PathVariable String placeId, @Valid @RequestBody ReportRequest request,
                                    HttpServletRequest http) {
        return reportService.submit(placeId, request, null, fingerprint.of(http));
    }

    @Operation(summary = "주차 정보 제보 (입구 사진 포함)",
            description = "multipart/form-data. `report` 파트에 JSON(위와 같은 모양, Content-Type: application/json), "
                    + "`photo` 파트에 JPG·PNG·WebP 사진 한 장 (5MB 이하).")
    @ApiResponse(responseCode = "201", description = "접수됨 (status = pending)")
    @ApiResponse(responseCode = "400", description = "INVALID_PHOTO 등",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "413", description = "사진이 5MB 초과",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping(path = "/places/{placeId}/reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ReportReceipt submitMultipart(@PathVariable String placeId,
                                         @Valid @RequestPart("report") ReportRequest request,
                                         @RequestPart(value = "photo", required = false) MultipartFile photo,
                                         HttpServletRequest http) {
        return reportService.submit(placeId, request, photo, fingerprint.of(http));
    }

    @Tag(name = "관리자")
    @SecurityRequirement(name = OpenApiConfig.ADMIN_KEY)
    @Operation(summary = "제보 목록", description = "status: pending(기본) / approved / rejected / 비우면 전체. 최신순.")
    @GetMapping("/api/admin/reports")
    public List<ReportView> list(@RequestParam(defaultValue = "pending") String status,
                                 @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return reportService.list(status, limit);
    }

    @Tag(name = "관리자")
    @SecurityRequirement(name = OpenApiConfig.ADMIN_KEY)
    @Operation(summary = "제보 승인",
            description = "lotId 가 있는 제보는 그 주차장 응답에 바로 반영됩니다 (비운 칸은 그대로, verifiedAt = 승인 시각).")
    @PostMapping("/api/admin/reports/{id}/approve")
    public ReportView approve(@PathVariable Long id, @Valid @RequestBody(required = false) ReviewRequest body) {
        return reportService.approve(id, body == null ? null : body.note());
    }

    @Tag(name = "관리자")
    @SecurityRequirement(name = OpenApiConfig.ADMIN_KEY)
    @Operation(summary = "제보 반려", description = "반려하면 함께 올린 사진 파일도 지웁니다.")
    @PostMapping("/api/admin/reports/{id}/reject")
    public ReportView reject(@PathVariable Long id, @Valid @RequestBody(required = false) ReviewRequest body) {
        return reportService.reject(id, body == null ? null : body.note());
    }
}
