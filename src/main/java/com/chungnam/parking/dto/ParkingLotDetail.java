package com.chungnam.parking.dto;

import com.chungnam.parking.domain.FeeInfo;
import com.chungnam.parking.domain.OperatingHours;
import com.chungnam.parking.domain.ParkingLot;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 상세 화면용 전체 정보
 */
public record ParkingLotDetail(
        Long id,
        String managementNo,
        String name,
        String region,
        String category,
        String lotType,
        String roadAddress,
        String jibunAddress,
        Integer totalSpaces,
        String operatingDays,
        Hours hours,
        Boolean openNow,
        String feeType,
        Fee fee,
        String paymentMethods,
        String notes,
        String managingAgency,
        String phoneNumber,
        Double latitude,
        Double longitude,
        LocalDate referenceDate
) {
    public record Hours(String weekday, String saturday, String holiday) {
        static Hours of(OperatingHours h) {
            if (h == null) return null;
            return new Hours(range(h.getWeekdayOpen(), h.getWeekdayClose()),
                    range(h.getSaturdayOpen(), h.getSaturdayClose()),
                    range(h.getHolidayOpen(), h.getHolidayClose()));
        }

        private static String range(String open, String close) {
            return (open == null || close == null) ? null : open + "~" + close;
        }
    }

    public record Fee(Integer basicTime, Integer basicCharge,
                      Integer additionalUnitTime, Integer additionalUnitCharge,
                      Integer dayPassCharge, Integer monthPassCharge) {
        static Fee of(FeeInfo f) {
            if (f == null) return null;
            return new Fee(f.getBasicTime(), f.getBasicCharge(), f.getAdditionalUnitTime(),
                    f.getAdditionalUnitCharge(), f.getDayPassCharge(), f.getMonthPassCharge());
        }
    }

    public static ParkingLotDetail of(ParkingLot p, LocalDateTime now) {
        return new ParkingLotDetail(
                p.getId(), p.getManagementNo(), p.getName(),
                p.getRegion().getLabel(), p.getCategory().getLabel(), p.getLotType(),
                p.getRoadAddress(), p.getJibunAddress(), p.getTotalSpaces(), p.getOperatingDays(),
                Hours.of(p.getOperatingHours()),
                p.getOperatingHours() == null ? null : p.getOperatingHours().isOpenAt(now),
                p.getFeeType().getLabel(), Fee.of(p.getFeeInfo()),
                p.getPaymentMethods(), p.getNotes(), p.getManagingAgency(), p.getPhoneNumber(),
                p.getLatitude(), p.getLongitude(), p.getReferenceDate()
        );
    }
}
