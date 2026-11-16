package com.chungnam.parking.place;

/**
 * 프론트 relationship: 'dedicated' | 'shared' | 'partner' | 'nearby'
 */
public enum ParkingRelationship {
    DEDICATED("dedicated"),   // 전용 → primaryParking
    SHARED("shared"),         // 건물 공용 → primaryParking
    PARTNER("partner"),       // 제휴 → primaryParking
    NEARBY("nearby");         // 주변 → alternatives

    private final String value;

    ParkingRelationship(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public boolean isPrimary() {
        return this != NEARBY;
    }
}
