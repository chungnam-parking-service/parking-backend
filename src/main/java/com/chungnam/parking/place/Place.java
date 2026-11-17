package com.chungnam.parking.place;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 목적지 (카페, 음식점 등). 프론트 Place 타입에 대응.
 */
@Entity
@Table(name = "place", indexes = @Index(name = "idx_place_search", columnList = "search_text"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place {

    /** 프론트에 그대로 노출되는 문자열 ID (예: "place-demo-cafe") */
    @Id
    @Column(length = 100)
    private String id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false, length = 300)
    private String address;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlaceParkingStatus parkingStatus;

    @Column(nullable = false, length = 200)
    private String parkingSummary;

    /** 목적지 운영시간 문구 (null 가능) */
    @Column(length = 200)
    private String hoursDescription;

    /** 연결 주차장 운영시간 경고 (null 가능) */
    @Column(length = 300)
    private String operatingHoursWarning;

    @Column(nullable = false)
    private boolean demo;

    /** true 면 주변 공공데이터 주차장을 alternatives 에 자동으로 붙인다 */
    @Column(nullable = false)
    private boolean includePublicNearby;

    /** 검색용: 이름+주소+카테고리에서 공백 제거, 소문자 (프론트 목업 검색 규칙과 동일) */
    @Column(name = "search_text", nullable = false, length = 600)
    private String searchText;

    @Builder
    private Place(String id, String name, String category, String address, double latitude, double longitude,
                  PlaceParkingStatus parkingStatus, String parkingSummary, String hoursDescription,
                  String operatingHoursWarning, boolean demo, boolean includePublicNearby) {
        this.id = id;
        this.name = name;
        this.category = category == null ? "" : category;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.parkingStatus = parkingStatus == null ? PlaceParkingStatus.UNKNOWN : parkingStatus;
        this.parkingSummary = parkingSummary;
        this.hoursDescription = hoursDescription;
        this.operatingHoursWarning = operatingHoursWarning;
        this.demo = demo;
        this.includePublicNearby = includePublicNearby;
        refreshSearchText();
    }

    @PrePersist
    @PreUpdate
    void refreshSearchText() {
        this.searchText = normalize(name + address + category);
    }

    public static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s", "").toLowerCase();
    }
}
