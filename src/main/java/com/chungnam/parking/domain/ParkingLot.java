package com.chungnam.parking.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "parking_lot", indexes = {
        @Index(name = "idx_parking_region", columnList = "region"),
        @Index(name = "idx_parking_lat_lng", columnList = "latitude, longitude"),
        @Index(name = "idx_parking_name", columnList = "name")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParkingLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 공공데이터 주차장관리번호 (동기화 시 upsert 키) */
    @Column(name = "management_no", nullable = false, unique = true, length = 50)
    private String managementNo;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChungnamRegion region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParkingCategory category;

    /** 노상 / 노외 / 부설 */
    @Column(length = 20)
    private String lotType;

    @Column(length = 300)
    private String roadAddress;

    @Column(length = 300)
    private String jibunAddress;

    private Integer totalSpaces;

    /** 운영요일 (예: 평일+토요일+공휴일) */
    @Column(length = 50)
    private String operatingDays;

    @Embedded
    private OperatingHours operatingHours;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeeType feeType;

    @Embedded
    private FeeInfo feeInfo;

    @Column(length = 200)
    private String paymentMethods;

    @Column(length = 1000)
    private String notes;

    @Column(length = 100)
    private String managingAgency;

    @Column(length = 30)
    private String phoneNumber;

    private Double latitude;

    private Double longitude;

    /** 공공데이터 기준일자 */
    private LocalDate referenceDate;

    @Column(nullable = false)
    private LocalDateTime syncedAt;

    // ===== 목적지 주차 안내용 (직접 등록/보완 정보) =====

    /** 데이터 출처. null 이면 공공데이터 */
    @Enumerated(EnumType.STRING)
    @Column(name = "lot_source", length = 20)
    private LotSource source;

    /** 실제 입구 좌표 (주차장 대표 좌표와 다를 수 있음) */
    private Double entranceLatitude;
    private Double entranceLongitude;

    @Column(length = 500)
    private String entrancePhotoUrl;

    @Column(length = 500)
    private String entranceGuide;

    /** 직접 입력한 요금 안내 문구. 없으면 공공데이터 요금으로 생성 */
    @Column(length = 300)
    private String feeDescription;

    /** 직접 입력한 운영시간 문구. 없으면 공공데이터 운영시간으로 생성 */
    @Column(length = 300)
    private String hoursDescription;

    /** 팀이 현장/전화로 마지막 확인한 시각 (Asia/Seoul) */
    private LocalDateTime verifiedAt;

    @Builder
    private ParkingLot(String managementNo, String name, ChungnamRegion region, ParkingCategory category,
                       String lotType, String roadAddress, String jibunAddress, Integer totalSpaces,
                       String operatingDays, OperatingHours operatingHours, FeeType feeType, FeeInfo feeInfo,
                       String paymentMethods, String notes, String managingAgency, String phoneNumber,
                       Double latitude, Double longitude, LocalDate referenceDate) {
        this.managementNo = managementNo;
        this.name = name;
        this.region = region;
        this.category = category;
        this.lotType = lotType;
        this.roadAddress = roadAddress;
        this.jibunAddress = jibunAddress;
        this.totalSpaces = totalSpaces;
        this.operatingDays = operatingDays;
        this.operatingHours = operatingHours;
        this.feeType = feeType;
        this.feeInfo = feeInfo;
        this.paymentMethods = paymentMethods;
        this.notes = notes;
        this.managingAgency = managingAgency;
        this.phoneNumber = phoneNumber;
        this.latitude = latitude;
        this.longitude = longitude;
        this.referenceDate = referenceDate;
        this.syncedAt = LocalDateTime.now();
    }

    /** 동기화 시 기존 엔티티를 최신 데이터로 갱신 */
    public void updateFrom(ParkingLot latest) {
        this.name = latest.name;
        this.region = latest.region;
        this.category = latest.category;
        this.lotType = latest.lotType;
        this.roadAddress = latest.roadAddress;
        this.jibunAddress = latest.jibunAddress;
        this.totalSpaces = latest.totalSpaces;
        this.operatingDays = latest.operatingDays;
        this.operatingHours = latest.operatingHours;
        this.feeType = latest.feeType;
        this.feeInfo = latest.feeInfo;
        this.paymentMethods = latest.paymentMethods;
        this.notes = latest.notes;
        this.managingAgency = latest.managingAgency;
        this.phoneNumber = latest.phoneNumber;
        this.latitude = latest.latitude;
        this.longitude = latest.longitude;
        this.referenceDate = latest.referenceDate;
        this.syncedAt = LocalDateTime.now();
    }

    /**
     * 공공데이터에 없는 주차장(건물 주차장 등)을 직접 등록할 때 사용.
     * externalId 는 프론트에 그대로 노출되는 ID (예: "lot-demo-cafe")
     */
    public static ParkingLot manual(String externalId, String name, String address,
                                    double entranceLatitude, double entranceLongitude,
                                    String entrancePhotoUrl, String entranceGuide,
                                    String feeDescription, String hoursDescription,
                                    LocalDateTime verifiedAt) {
        ChungnamRegion region = ChungnamRegion.fromAddress(address)
                .orElseThrow(() -> new IllegalArgumentException("충남 시·군을 주소에서 찾을 수 없습니다: " + address));
        ParkingLot lot = ParkingLot.builder()
                .managementNo(externalId)
                .name(name)
                .region(region)
                .category(ParkingCategory.UNKNOWN)
                .roadAddress(address)
                .feeType(FeeType.UNKNOWN)
                .latitude(entranceLatitude)
                .longitude(entranceLongitude)
                .build();
        lot.source = LotSource.MANUAL;
        lot.entranceLatitude = entranceLatitude;
        lot.entranceLongitude = entranceLongitude;
        lot.entrancePhotoUrl = entrancePhotoUrl;
        lot.entranceGuide = entranceGuide;
        lot.feeDescription = feeDescription;
        lot.hoursDescription = hoursDescription;
        lot.verifiedAt = verifiedAt;
        return lot;
    }

    public boolean isManual() {
        return source == LotSource.MANUAL;
    }

    public String getDisplayAddress() {
        return (roadAddress != null && !roadAddress.isBlank()) ? roadAddress : jibunAddress;
    }
}
