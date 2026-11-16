package com.chungnam.parking.domain;

/**
 * 요금 정보 (공공데이터 parkingchrgeInfo)
 */
public enum FeeType {
    FREE("무료"),
    PAID("유료"),
    MIXED("혼합"),
    UNKNOWN("정보없음");

    private final String label;

    FeeType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static FeeType fromLabel(String value) {
        if (value == null) return UNKNOWN;
        if (value.contains("무료")) return FREE;
        if (value.contains("유료")) return PAID;
        if (value.contains("혼합")) return MIXED;
        return UNKNOWN;
    }
}
