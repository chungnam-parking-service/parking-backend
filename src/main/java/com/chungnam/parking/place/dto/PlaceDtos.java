package com.chungnam.parking.place.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 프론트 src/domain/parking.ts 타입과 1:1 대응하는 응답 DTO.
 * 필드명·null 규칙을 바꾸면 프론트 타입도 같이 바꿔야 한다.
 * (null 필드를 생략하지 않도록 @JsonInclude(NON_NULL) 을 붙이지 않는다)
 */
public final class PlaceDtos {

    private PlaceDtos() {
    }

    /** Coordinates { latitude, longitude } */
    public record Coordinates(double latitude, double longitude) {
    }

    /** Place */
    public record PlaceDto(
            String id,
            String name,
            String category,
            String address,
            Coordinates coordinates,
            String parkingStatus,     // available | unavailable | unknown
            String parkingSummary
    ) {
    }

    /** SearchPlacesResponse { items, total } */
    public record SearchPlacesResponse(List<PlaceDto> items, int total) {
    }

    /** ParkingLot */
    public record ParkingLotDto(
            String id,
            String name,
            String address,
            Coordinates entranceCoordinates,
            String entrancePhotoUrl,        // null 가능
            String entranceGuide,           // null 가능
            String feeDescription,          // null 가능
            String hoursDescription,        // null 가능
            OffsetDateTime verifiedAt,      // null 가능, "2026-11-07T09:00:00+09:00"
            String coordinateType           // entrance(실제 입구) | representative(주차장 대표 좌표) | unknown
    ) {
    }

    /** PlaceParkingOption */
    public record PlaceParkingOption(
            ParkingLotDto lot,
            String relationship,            // dedicated | shared | partner | nearby
            String benefitDescription,      // null 가능
            String validationInstructions,  // null 가능
            Integer walkingMinutes,         // null 가능. 직접 확인·등록한 값만 (추정값은 아래 필드)
            List<String> walkingSteps,      // 미확인이면 []
            // v1.2 선택 필드 (디자인의 "도보 거리 약 250m · 약 3분"). 값이 없으면 필드 자체를 생략한다.
            @JsonInclude(JsonInclude.Include.NON_NULL)
            Integer distanceMeters,           // 목적지 ↔ 주차장 직선거리(m)
            @JsonInclude(JsonInclude.Include.NON_NULL)
            Integer estimatedWalkingMinutes   // 직선거리 × 1.3 ÷ 75m/분 추정. 화면에는 "약 N분"
    ) {
        /** 기존 6개 필드 생성자 (추정 정보 없음) */
        public PlaceParkingOption(ParkingLotDto lot, String relationship, String benefitDescription,
                                  String validationInstructions, Integer walkingMinutes, List<String> walkingSteps) {
            this(lot, relationship, benefitDescription, validationInstructions, walkingMinutes, walkingSteps, null, null);
        }
    }

    /** PlaceParkingResponse */
    public record PlaceParkingResponse(
            PlaceDto place,
            String placeHoursDescription,   // null 가능
            String operatingHoursWarning,   // null 가능
            List<PlaceParkingOption> primaryParking,
            List<PlaceParkingOption> alternatives,
            boolean isDemo
    ) {
    }
}
