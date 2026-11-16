package com.chungnam.parking.dto;

public record FeeEstimateResponse(
        Long parkingLotId,
        String name,
        int minutes,
        String feeType,
        Integer estimatedFee,
        String message
) {
}
