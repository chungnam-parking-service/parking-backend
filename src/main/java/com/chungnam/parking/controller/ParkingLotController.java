package com.chungnam.parking.controller;

import com.chungnam.parking.common.ErrorResponse;
import com.chungnam.parking.domain.FeeType;
import com.chungnam.parking.dto.*;
import com.chungnam.parking.service.ParkingLotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "주차장", description = "주차장 검색 / 주변 검색 / 상세 / 요금")
@RestController
@RequestMapping("/api/parking-lots")
@RequiredArgsConstructor
@Validated
public class ParkingLotController {

    private final ParkingLotService parkingLotService;

    @Operation(summary = "주차장 검색",
            description = "지역·키워드·요금·구분 조건으로 검색합니다. 조건은 모두 선택값이며 넣은 것만 적용됩니다. "
                    + "정렬 예: sort=name,asc / sort=totalSpaces,desc")
    @GetMapping
    public PageResponse<ParkingLotSummary> search(
            @ParameterObject @Valid @ModelAttribute ParkingSearchCondition condition,
            @ParameterObject @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return parkingLotService.search(condition, pageable);
    }

    @Operation(summary = "내 주변 주차장",
            description = "현재 위치(위도·경도) 기준 반경 안의 주차장을 가까운 순으로 반환합니다. distanceMeters 는 직선거리(m)입니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "400", description = "좌표가 충남 범위를 벗어남",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/nearby")
    public List<ParkingLotSummary> nearby(
            @Parameter(description = "위도 (충남 범위 35.9 ~ 37.1)", example = "36.819")
            @RequestParam @DecimalMin("35.9") @DecimalMax("37.1") double lat,
            @Parameter(description = "경도 (충남 범위 125.9 ~ 127.8)", example = "127.156")
            @RequestParam @DecimalMin("125.9") @DecimalMax("127.8") double lng,
            @Parameter(description = "반경(m), 100 ~ 10000", example = "1000")
            @RequestParam(defaultValue = "1000") @Min(100) @Max(ParkingLotService.MAX_RADIUS_METERS) int radius,
            @Parameter(description = "최대 개수, 1 ~ 200", example = "50")
            @RequestParam(defaultValue = "50") @Min(1) @Max(ParkingLotService.MAX_NEARBY_LIMIT) int limit,
            @Parameter(description = "요금 구분 필터 (선택)")
            @RequestParam(required = false) FeeType feeType) {
        return parkingLotService.findNearby(lat, lng, radius, limit, feeType);
    }

    @Operation(summary = "주차장 상세", description = "운영시간, 요금 상세, 연락처 등 전체 정보를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "404", description = "주차장 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    public ParkingLotDetail detail(@Parameter(description = "주차장 ID", example = "1") @PathVariable Long id) {
        return parkingLotService.getDetail(id);
    }

    @Operation(summary = "예상 주차요금",
            description = "공공데이터의 기본요금·추가요금·1일권 정보로 예상 요금을 계산합니다. 1일권이 더 싸면 1일권 요금을 반환합니다.")
    @GetMapping("/{id}/fee")
    public FeeEstimateResponse estimateFee(
            @Parameter(description = "주차장 ID", example = "1") @PathVariable Long id,
            @Parameter(description = "주차 시간(분)", example = "90") @RequestParam @Min(1) @Max(1440 * 31) int minutes) {
        return parkingLotService.estimateFee(id, minutes);
    }
}
