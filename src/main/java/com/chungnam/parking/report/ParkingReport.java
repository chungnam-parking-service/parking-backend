package com.chungnam.parking.report;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 사용자가 보낸 주차 정보 등록·수정 제보. 관리자가 승인하면 목적지 주차 응답에 덧씌워진다(ReportOverlay).
 * 비어 있는 칸은 "바꾸지 않음"이다.
 */
@Entity
@Table(name = "parking_report", indexes = {
        @Index(name = "idx_report_place_status", columnList = "place_id, status"),
        @Index(name = "idx_report_client", columnList = "client_hash, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParkingReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "place_id", nullable = false, length = 100)
    private String placeId;

    /** 화면에 보이는 주차장 ID. null 이면 목적지 전체에 대한 제보(새 주차장 알림 등) — 승인해도 자동 반영되지 않는다 */
    @Column(name = "lot_id", length = 100)
    private String lotId;

    /** MySQL 네이티브 enum 이 되지 않도록 문자열로 저장 (값을 늘려도 ALTER 불필요) */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    @Column(name = "entrance_guide", length = 500)
    private String entranceGuide;

    @Column(name = "fee_description", length = 300)
    private String feeDescription;

    @Column(name = "hours_description", length = 300)
    private String hoursDescription;

    @Column(name = "benefit_description", length = 300)
    private String benefitDescription;

    @Column(name = "validation_instructions", length = 500)
    private String validationInstructions;

    /** 도보 안내 단계를 줄바꿈으로 이어 저장 */
    @Column(name = "walking_steps", length = 3100)
    private String walkingSteps;

    @Column(name = "entrance_latitude")
    private Double entranceLatitude;

    @Column(name = "entrance_longitude")
    private Double entranceLongitude;

    /** 서버 기준 경로 (/uploads/xxx.jpg). 응답에서는 절대 URL 로 바꾼다 */
    @Column(name = "photo_path", length = 200)
    private String photoPath;

    /** 관리자에게만 보이는 메모 */
    @Column(length = 1000)
    private String comment;

    @Column(name = "client_hash", nullable = false, length = 64)
    private String clientHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_note", length = 300)
    private String reviewNote;

    ParkingReport(String placeId, String lotId, String entranceGuide, String feeDescription, String hoursDescription,
                  String benefitDescription, String validationInstructions, List<String> walkingSteps,
                  Double entranceLatitude, Double entranceLongitude, String photoPath, String comment,
                  String clientHash, LocalDateTime createdAt) {
        this.placeId = placeId;
        this.lotId = lotId;
        this.status = ReportStatus.PENDING;
        this.entranceGuide = entranceGuide;
        this.feeDescription = feeDescription;
        this.hoursDescription = hoursDescription;
        this.benefitDescription = benefitDescription;
        this.validationInstructions = validationInstructions;
        this.walkingSteps = walkingSteps == null || walkingSteps.isEmpty() ? null : String.join("\n", walkingSteps);
        this.entranceLatitude = entranceLatitude;
        this.entranceLongitude = entranceLongitude;
        this.photoPath = photoPath;
        this.comment = comment;
        this.clientHash = clientHash;
        this.createdAt = createdAt;
    }

    public List<String> walkingStepList() {
        return walkingSteps == null ? List.of() : Arrays.stream(walkingSteps.split("\n")).filter(s -> !s.isBlank()).toList();
    }

    public boolean hasEntrance() {
        return entranceLatitude != null && entranceLongitude != null;
    }

    void approve(LocalDateTime at, String note) {
        this.status = ReportStatus.APPROVED;
        this.reviewedAt = at;
        this.reviewNote = note;
    }

    void reject(LocalDateTime at, String note) {
        this.status = ReportStatus.REJECTED;
        this.reviewedAt = at;
        this.reviewNote = note;
        this.photoPath = null;   // 반려된 사진은 지운다 (ReportService 가 파일도 삭제)
    }
}
