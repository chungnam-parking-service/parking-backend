package com.chungnam.parking.report;

import com.chungnam.parking.place.dto.PlaceDtos.Coordinates;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public final class ReportDtos {

    private ReportDtos() {
    }

    /** 주차 정보 등록·수정 제보. 모르는 칸은 비워 두면 된다 (비운 칸은 바꾸지 않음) */
    public record ReportRequest(
            @Schema(description = "고칠 주차장의 lot.id. 비우면 목적지 전체에 대한 제보(새 주차장 알림 등)", example = "osm-way-472630487")
            @Size(max = 100) String lotId,
            @Schema(description = "차량 진입 안내", example = "건물 뒤편 골목으로 들어가면 지하 입구가 있어요")
            @Size(max = 500) String entranceGuide,
            @Schema(description = "요금", example = "최초 30분 무료, 이후 10분당 500원")
            @Size(max = 300) String feeDescription,
            @Schema(description = "운영시간", example = "매일 08:00–22:00")
            @Size(max = 300) String hoursDescription,
            @Schema(description = "이 목적지에서 받는 혜택", example = "음료 구매 시 1시간 무료")
            @Size(max = 300) String benefitDescription,
            @Schema(description = "주차 등록·정산 방법", example = "카운터에 차량번호를 알려주세요")
            @Size(max = 500) String validationInstructions,
            @Schema(description = "주차장 → 목적지 도보 안내 (최대 10단계, 각 300자)")
            @Size(max = 10) List<@Size(max = 300) String> walkingSteps,
            @Schema(description = "실제 차량 입구 위도 (경도와 함께)", example = "36.8148")
            @DecimalMin("33.0") @DecimalMax("39.0") Double entranceLatitude,
            @Schema(description = "실제 차량 입구 경도 (위도와 함께)", example = "127.1082")
            @DecimalMin("124.0") @DecimalMax("132.0") Double entranceLongitude,
            @Schema(description = "관리자에게 남길 말 (화면에 노출되지 않음)")
            @Size(max = 1000) String comment) {
    }

    /** 제보 접수 결과 */
    public record ReportReceipt(Long id, String status, String placeId, String lotId, String photoUrl,
                                OffsetDateTime createdAt) {
    }

    /** 관리자 화면용 전체 내용 */
    public record ReportView(Long id, String status, String placeId, String lotId,
                             String entranceGuide, String feeDescription, String hoursDescription,
                             String benefitDescription, String validationInstructions, List<String> walkingSteps,
                             Coordinates entranceCoordinates, String photoUrl, String comment,
                             OffsetDateTime createdAt, OffsetDateTime reviewedAt, String reviewNote,
                             @Schema(description = "승인하면 목적지 응답에 자동 반영되는지 (lotId 가 있어야 반영)")
                             boolean autoApplies) {
    }

    public record ReviewRequest(@Size(max = 300) String note) {
    }
}
