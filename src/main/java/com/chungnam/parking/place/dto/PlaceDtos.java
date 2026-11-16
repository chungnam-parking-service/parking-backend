package com.chungnam.parking.place.dto;

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
            OffsetDateTime verifiedAt       // null 가능, "2026-11-07T09:00:00+09:00"
    ) {
    }

    /** PlaceParkingOption */
    public record PlaceParkingOption(
            ParkingLotDto lot,
            String relationship,            // dedicated | shared | partner | nearby
            String benefitDescription,      // null 가능
            String validationInstructions,  // null 가능
            Integer walkingMinutes,         // null 가능
            List<String> walkingSteps       // 미확인이면 []
    ) {
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
