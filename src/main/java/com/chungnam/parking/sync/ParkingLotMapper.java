package com.chungnam.parking.sync;

import com.chungnam.parking.domain.*;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 공공데이터 Item → ParkingLot 엔티티 변환
 */
public final class ParkingLotMapper {

    private ParkingLotMapper() {
    }

    /** 충남 데이터가 아니거나 필수값이 없으면 empty */
    public static Optional<ParkingLot> toEntity(PublicParkingApiResponse.Item item) {
        String address = firstNonBlank(item.rdnmadr(), item.lnmadr());
        if (!ChungnamRegion.isChungnam(address) || isBlank(item.prkplceNo()) || isBlank(item.prkplceNm())) {
            return Optional.empty();
        }
        Optional<ChungnamRegion> region = ChungnamRegion.fromAddress(address);
        if (region.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(ParkingLot.builder()
                .managementNo(item.prkplceNo().trim())
                .name(item.prkplceNm().trim())
                .region(region.get())
                .category(ParkingCategory.fromLabel(item.prkplceSe()))
                .lotType(trim(item.prkplceType()))
                .roadAddress(trim(item.rdnmadr()))
                .jibunAddress(trim(item.lnmadr()))
                .totalSpaces(toInt(item.prkcmprt()))
                .operatingDays(trim(item.operDay()))
                .operatingHours(new OperatingHours(
                        time(item.weekdayOperOpenHhmm()), time(item.weekdayOperColseHhmm()),
                        time(item.satOperOperOpenHhmm()), time(item.satOperCloseHhmm()),
                        time(item.holidayOperOpenHhmm()), time(item.holidayCloseOpenHhmm())))
                .feeType(FeeType.fromLabel(item.parkingchrgeInfo()))
                .feeInfo(new FeeInfo(
                        toInt(item.basicTime()), toInt(item.basicCharge()),
                        toInt(item.addUnitTime()), toInt(item.addUnitCharge()),
                        toInt(item.dayCmmtkt()), toInt(item.monthCmmtkt())))
                .paymentMethods(trim(item.metpay()))
                .notes(truncate(trim(item.spcmnt()), 1000))
                .managingAgency(trim(item.institutionNm()))
                .phoneNumber(trim(item.phoneNumber()))
                .latitude(toDouble(item.latitude()))
                .longitude(toDouble(item.longitude()))
                .referenceDate(toDate(item.referenceDate()))
                .build());
    }

    /** "0900" / "09:00" / "9:00" → "09:00" */
    static String time(String raw) {
        if (isBlank(raw)) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() == 3) digits = "0" + digits;
        if (digits.length() != 4) return null;
        return digits.substring(0, 2) + ":" + digits.substring(2);
    }

    static Integer toInt(String raw) {
        if (isBlank(raw)) return null;
        try {
            return (int) Double.parseDouble(raw.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Double toDouble(String raw) {
        if (isBlank(raw)) return null;
        try {
            double v = Double.parseDouble(raw.trim());
            return v == 0 ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static LocalDate toDate(String raw) {
        if (isBlank(raw)) return null;
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonBlank(String a, String b) {
        return !isBlank(a) ? a.trim() : (b == null ? null : b.trim());
    }

    private static String trim(String s) {
        return isBlank(s) ? null : s.trim();
    }

    private static String truncate(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
