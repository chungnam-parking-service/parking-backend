package com.chungnam.parking.place;

import com.chungnam.parking.config.OpenApiConfig;
import com.chungnam.parking.place.dto.PlaceAdminDtos.UpsertPlaceRequest;
import com.chungnam.parking.place.dto.PlaceDtos.PlaceParkingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "관리자", description = "데이터 관리 (프론트에서 사용하지 않음)")
@SecurityRequirement(name = OpenApiConfig.ADMIN_KEY)
@RestController
@RequestMapping("/api/admin/places")
@RequiredArgsConstructor
public class PlaceAdminController {

    /** AI 파트 데이터의 ID(sangga-MA0101…)처럼 대문자가 있는 ID도 받는다. DB 컬럼은 100자 */
    private static final String ID = "[A-Za-z0-9][A-Za-z0-9_-]{0,98}";

    private final PlaceAdminService adminService;

    @Operation(summary = "목적지 등록·수정",
            description = "같은 placeId 로 다시 보내면 목적지와 연결 주차장을 통째로 교체합니다. "
                    + "응답은 GET /places/{placeId}/parking 과 같습니다. "
                    + "parking[] 의 각 항목은 lot(직접 등록) 또는 existingLotId(공공데이터 'lot-138') 중 하나를 보냅니다.")
    @PutMapping("/{placeId}")
    public PlaceParkingResponse upsert(
            @PathVariable @Pattern(regexp = ID, message = "placeId 는 영문·숫자·하이픈·밑줄 99자 이내여야 합니다.") String placeId,
            @Valid @RequestBody UpsertPlaceRequest body) {
        return adminService.upsert(placeId, body);
    }

    @Operation(summary = "목적지 삭제", description = "목적지와 연결만 지웁니다. 직접 등록한 주차장은 남습니다.")
    @DeleteMapping("/{placeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Pattern(regexp = ID) String placeId) {
        adminService.delete(placeId);
    }
}
