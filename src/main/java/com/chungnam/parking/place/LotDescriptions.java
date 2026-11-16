package com.chungnam.parking.place;

import com.chungnam.parking.domain.FeeInfo;
import com.chungnam.parking.domain.FeeType;
import com.chungnam.parking.domain.OperatingHours;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 공공데이터의 요금·운영시간 숫자를 프론트용 안내 문구로 바꾼다.
 * 정보가 없으면 null 을 반환한다 (무료·0원으로 바꾸지 않음).
 */
final class LotDescriptions {

    private static final String DASH = "–";

    private LotDescriptions() {
    }

    /** 예: "기본 30분 500원 · 추가 10분당 200원 · 1일 5,000원" / "무료" / null */
    static String fee(FeeType feeType, FeeInfo fee) {
        if (feeType == FeeType.FREE) {
            return "무료";
        }
        List<String> parts = new ArrayList<>();
        if (fee != null) {
            if (fee.getBasicTime() != null && fee.getBasicCharge() != null) {
                parts.add("기본 " + fee.getBasicTime() + "분 " + won(fee.getBasicCharge()));
            }
            if (fee.getAdditionalUnitTime() != null && fee.getAdditionalUnitTime() > 0
                    && fee.getAdditionalUnitCharge() != null) {
                parts.add("추가 " + fee.getAdditionalUnitTime() + "분당 " + won(fee.getAdditionalUnitCharge()));
            }
            if (fee.getDayPassCharge() != null && fee.getDayPassCharge() > 0) {
                parts.add("1일 " + won(fee.getDayPassCharge()));
            }
        }
        if (parts.isEmpty()) {
            return feeType == FeeType.MIXED ? "유료·무료 혼합 (상세 요금 정보 없음)" : null;
        }
        String text = String.join(" · ", parts);
        return feeType == FeeType.MIXED ? text + " (일부 무료)" : text;
    }

    /** 예: "24시간" / "매일 09:00–21:00" / "평일 09:00–18:00 · 토 09:00–13:00" / null */
    static String hours(OperatingHours h) {
        if (h == null) {
            return null;
        }
        String weekday = range(h.getWeekdayOpen(), h.getWeekdayClose());
        String saturday = range(h.getSaturdayOpen(), h.getSaturdayClose());
        String holiday = range(h.getHolidayOpen(), h.getHolidayClose());

        if (weekday != null && weekday.equals(saturday) && weekday.equals(holiday)) {
            return isAllDay(weekday) ? "24시간" : "매일 " + weekday;
        }
        List<String> parts = new ArrayList<>();
        if (weekday != null) parts.add("평일 " + weekday);
        if (saturday != null) parts.add("토 " + saturday);
        if (holiday != null) parts.add("공휴일 " + holiday);
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    private static String range(String open, String close) {
        return (open == null || close == null) ? null : open + DASH + close;
    }

    private static boolean isAllDay(String range) {
        String[] t = range.split(DASH);
        return t[0].equals(t[1]) || ("00:00".equals(t[0]) && ("23:59".equals(t[1]) || "24:00".equals(t[1])));
    }

    private static String won(int amount) {
        return NumberFormat.getIntegerInstance(Locale.KOREA).format(amount) + "원";
    }
}
