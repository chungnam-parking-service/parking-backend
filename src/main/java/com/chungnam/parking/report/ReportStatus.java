package com.chungnam.parking.report;

import java.util.Locale;

/** 제보 처리 상태. 응답에서는 소문자 (pending | approved | rejected) */
public enum ReportStatus {
    PENDING,
    APPROVED,
    REJECTED;

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ReportStatus from(String value) {
        try {
            return ReportStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("status 는 pending, approved, rejected 중 하나입니다.");
        }
    }
}
