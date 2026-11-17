package com.chungnam.parking.place.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 관리자 목적지 등록(PUT /api/admin/places/{placeId}) 요청 본문.
 * 값의 표기는 응답과 같다: parkingStatus 는 available|unavailable|unknown,
 * relationship 은 dedicated|shared|partner|nearby (소문자).
 * 길이 제한은 DB 컬럼 크기와 같고, 좌표는 충남 범위다.
 */
public final class PlaceAdminDtos {

    private PlaceAdminDtos() {
    }

    public record UpsertPlaceRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 50) String category,
            @NotBlank @Size(max = 300) String address,
            @NotNull @DecimalMin("35.9") @DecimalMax("37.1") Double latitude,
            @NotNull @DecimalMin("125.9") @DecimalMax("127.8") Double longitude,
            @NotNull @Pattern(regexp = "available|unavailable|unknown") String parkingStatus,
            @NotBlank @Size(max = 200) String parkingSummary,
            @Size(max = 200) String hoursDescription,
            @Size(max = 300) String operatingHoursWarning,
            /** true 면 반경 1km 공공데이터 주차장을 alternatives 에 자동 추가 */
            boolean includePublicNearby,
            @Size(max = 20) List<@Valid ParkingLinkRequest> parking
    ) {
    }

    /** lot(새로 등록·갱신) 과 existingLotId(공공데이터 주차장 연결) 중 정확히 하나를 보낸다 */
    public record ParkingLinkRequest(
            @NotNull @Pattern(regexp = "dedicated|shared|partner|nearby") String relationship,
            @Valid ManualLotRequest lot,
            @Pattern(regexp = "lot-\\d+") String existingLotId,
            @Size(max = 300) String benefitDescription,
            @Size(max = 500) String validationInstructions,
            @Min(0) @Max(240) Integer walkingMinutes,
            @Size(max = 20) List<@NotBlank @Size(max = 300) String> walkingSteps
    ) {
    }

    /** 공공데이터에 없는 주차장(건물 주차장 등). 좌표는 실제 입구 좌표 */
    public record ManualLotRequest(
            /** 영문·숫자·하이픈·밑줄, 50자 이내. 공공데이터 형식(lot-숫자)은 사용 불가 */
            @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,48}") String id,
            @NotBlank @Size(max = 200) String name,
            /** 주소에서 충남 시·군을 찾는다 (예: "충남 천안시 ...") */
            @NotBlank @Size(max = 300) String address,
            @NotNull @DecimalMin("35.9") @DecimalMax("37.1") Double latitude,
            @NotNull @DecimalMin("125.9") @DecimalMax("127.8") Double longitude,
            @Size(max = 500) String entrancePhotoUrl,
            @Size(max = 500) String entranceGuide,
            @Size(max = 300) String feeDescription,
            @Size(max = 300) String hoursDescription,
            OffsetDateTime verifiedAt
    ) {
    }
}
