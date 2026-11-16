package com.chungnam.parking.domain;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 요금 상세 (단위: 분 / 원)
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FeeInfo {

    private Integer basicTime;
    private Integer basicCharge;
    private Integer additionalUnitTime;
    private Integer additionalUnitCharge;
    private Integer dayPassCharge;
    private Integer monthPassCharge;

    /**
     * 주차 시간(분)에 대한 예상 요금 계산. 1일권이 더 싸면 1일권 요금을 반환.
     */
    public Integer estimate(int minutes) {
        if (basicCharge == null || basicTime == null) return null;
        int fee = basicCharge;
        int remaining = minutes - basicTime;
        if (remaining > 0 && additionalUnitTime != null && additionalUnitTime > 0 && additionalUnitCharge != null) {
            int units = (int) Math.ceil((double) remaining / additionalUnitTime);
            fee += units * additionalUnitCharge;
        }
        if (dayPassCharge != null && dayPassCharge > 0 && minutes <= 24 * 60) {
            fee = Math.min(fee, dayPassCharge);
        }
        return fee;
    }
}
