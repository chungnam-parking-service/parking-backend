package com.chungnam.parking.dto;

import com.chungnam.parking.domain.ChungnamRegion;
import com.chungnam.parking.domain.FeeType;
import com.chungnam.parking.domain.ParkingCategory;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Min;

/**
 * GET /api/parking-lots 쿼리 파라미터 (모두 선택값)
 */
public record ParkingSearchCondition(
        @Parameter(description = "주차장명 또는 주소 검색어", example = "터미널")
        String keyword,
        @Parameter(description = "시·군 코드 (GET /api/regions 의 code 값)")
        ChungnamRegion region,
        @Parameter(description = "PUBLIC(공영) / PRIVATE(민영)")
        ParkingCategory category,
        @Parameter(description = "FREE(무료) / PAID(유료) / MIXED(혼합)")
        FeeType feeType,
        @Parameter(description = "최소 주차면수", example = "50")
        @Min(0) Integer minSpaces,
        @Parameter(description = "true 면 지금 운영 중인 주차장만")
        Boolean openNow
) {
}
