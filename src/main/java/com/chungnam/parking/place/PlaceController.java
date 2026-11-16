package com.chungnam.parking.place;

import com.chungnam.parking.common.ErrorResponse;
import com.chungnam.parking.place.dto.PlaceDtos.PlaceParkingResponse;
import com.chungnam.parking.place.dto.PlaceDtos.SearchPlacesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 프론트 docs/API.md 계약 그대로의 경로 (/api 접두사 없음).
 * 프론트 .env: VITE_API_BASE_URL=http://localhost:8080
 */
@Tag(name = "목적지 (프론트 API.md)", description = "목적지 검색과 목적지별 주차 안내")
@RestController
@RequestMapping("/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceService placeService;

    @Operation(summary = "목적지 검색",
            description = "이름·주소·카테고리로 검색합니다(공백 무시). 빈 검색어는 빈 목록, 100자 초과는 400. 페이지네이션 없음.")
    @GetMapping
    public SearchPlacesResponse search(
            @Parameter(description = "검색어 (앞뒤 공백 제거 후 1–100자)", example = "천안")
            @RequestParam(required = false) String query) {
        return placeService.search(query);
    }

    @Operation(summary = "목적지 주차 상세",
            description = "primaryParking 에는 전용·공용·제휴 주차장, alternatives 에는 주변 주차장(도보 시간 오름차순, 미확인은 마지막). "
                    + "모르는 값은 null 또는 빈 배열입니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "404", description = "PLACE_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{placeId}/parking")
    public PlaceParkingResponse parking(
            @Parameter(description = "검색 응답의 place.id", example = "place-demo-cafe")
            @PathVariable String placeId) {
        return placeService.getParking(placeId);
    }
}
