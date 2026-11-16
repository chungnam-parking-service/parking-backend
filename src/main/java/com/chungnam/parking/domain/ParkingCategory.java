package com.chungnam.parking.domain;

/**
 * 주차장 구분 (공공데이터 prkplceSe)
 */
public enum ParkingCategory {
    PUBLIC("공영"),
    PRIVATE("민영"),
    UNKNOWN("기타");

    private final String label;

    ParkingCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ParkingCategory fromLabel(String value) {
        if (value == null) return UNKNOWN;
        if (value.contains("공영")) return PUBLIC;
        if (value.contains("민영")) return PRIVATE;
        return UNKNOWN;
    }
}
