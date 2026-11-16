package com.chungnam.parking.place;

/**
 * 프론트 ParkingStatus: 'available' | 'unavailable' | 'unknown'
 */
public enum PlaceParkingStatus {
    /** 목적지에서 이용 가능한 주차 시설이 확인됨 (실시간 빈자리 아님) */
    AVAILABLE("available"),
    /** 목적지 자체·연결 주차가 불가한 것으로 확인됨 */
    UNAVAILABLE("unavailable"),
    /** 모름. 불가로 취급하지 않음 */
    UNKNOWN("unknown");

    private final String value;

    PlaceParkingStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
