package com.chungnam.parking.dto;

import com.chungnam.parking.domain.ParkingLot;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * 목록 / 지도 마커용 요약 정보
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParkingLotSummary(
        Long id,
        String name,
        String region,
        String category,
        String feeType,
        String address,
        Integer totalSpaces,
        Double latitude,
        Double longitude,
        Boolean openNow,
        Integer distanceMeters
) {
    public static ParkingLotSummary of(ParkingLot p, LocalDateTime now) {
        return of(p, now, null);
    }

    public static ParkingLotSummary of(ParkingLot p, LocalDateTime now, Double distance) {
        return new ParkingLotSummary(
                p.getId(),
                p.getName(),
                p.getRegion().getLabel(),
                p.getCategory().getLabel(),
                p.getFeeType().getLabel(),
                p.getDisplayAddress(),
                p.getTotalSpaces(),
                p.getLatitude(),
                p.getLongitude(),
                p.getOperatingHours() == null ? null : p.getOperatingHours().isOpenAt(now),
                distance == null ? null : (int) Math.round(distance)
        );
    }
}
