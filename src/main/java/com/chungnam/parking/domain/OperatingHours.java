package com.chungnam.parking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 운영시간 ("HH:mm" 문자열로 저장, 00:00~00:00 / 00:00~23:59 는 24시간 운영)
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class OperatingHours {

    @Column(length = 5) private String weekdayOpen;
    @Column(length = 5) private String weekdayClose;
    @Column(length = 5) private String saturdayOpen;
    @Column(length = 5) private String saturdayClose;
    @Column(length = 5) private String holidayOpen;
    @Column(length = 5) private String holidayClose;

    /**
     * 주어진 시각에 운영 중인지 판단 (일요일은 공휴일 시간 기준).
     * 시간 정보가 없으면 null(알 수 없음)을 반환한다.
     */
    public Boolean isOpenAt(LocalDateTime dateTime) {
        DayOfWeek day = dateTime.getDayOfWeek();
        String open, close;
        if (day == DayOfWeek.SATURDAY) {
            open = saturdayOpen; close = saturdayClose;
        } else if (day == DayOfWeek.SUNDAY) {
            open = holidayOpen; close = holidayClose;
        } else {
            open = weekdayOpen; close = weekdayClose;
        }
        if (open == null || close == null) return null;

        try {
            LocalTime openTime = LocalTime.parse(open);
            LocalTime closeTime = "24:00".equals(close) ? LocalTime.MAX : LocalTime.parse(close);
            LocalTime now = dateTime.toLocalTime();

            if (openTime.equals(closeTime) || (openTime.equals(LocalTime.MIDNIGHT) && closeTime.getHour() == 23 && closeTime.getMinute() == 59)) {
                return true; // 24시간
            }
            if (openTime.isBefore(closeTime)) {
                return !now.isBefore(openTime) && now.isBefore(closeTime);
            }
            // 자정을 넘기는 운영 (예: 18:00 ~ 02:00)
            return !now.isBefore(openTime) || now.isBefore(closeTime);
        } catch (Exception e) {
            return null;
        }
    }
}
