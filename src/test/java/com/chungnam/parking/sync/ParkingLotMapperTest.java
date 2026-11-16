package com.chungnam.parking.sync;

import com.chungnam.parking.domain.ChungnamRegion;
import com.chungnam.parking.domain.FeeType;
import com.chungnam.parking.domain.ParkingCategory;
import com.chungnam.parking.domain.ParkingLot;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ParkingLotMapperTest {

    private PublicParkingApiResponse.Item item(String roadAddr) {
        return new PublicParkingApiResponse.Item(
                "123-4-000001", "아산시청 주차장", "공영", "노외", roadAddr, null, "120",
                "평일+토요일", "0900", "1800", "09:00", "13:00", "", "",
                "유료", "30", "500", "10", "200", "5,000", "", "카드", "", "아산시청", "041-000-0000",
                "36.7898", "127.0018", "2026-10-01");
    }

    @Test
    void 충남_데이터_변환() {
        Optional<ParkingLot> result = ParkingLotMapper.toEntity(item("충청남도 아산시 시민로 456"));

        assertThat(result).isPresent();
        ParkingLot p = result.get();
        assertThat(p.getRegion()).isEqualTo(ChungnamRegion.ASAN);
        assertThat(p.getCategory()).isEqualTo(ParkingCategory.PUBLIC);
        assertThat(p.getFeeType()).isEqualTo(FeeType.PAID);
        assertThat(p.getTotalSpaces()).isEqualTo(120);
        assertThat(p.getOperatingHours().getWeekdayOpen()).isEqualTo("09:00");
        assertThat(p.getOperatingHours().getHolidayOpen()).isNull();
        assertThat(p.getFeeInfo().getDayPassCharge()).isEqualTo(5000);
    }

    @Test
    void 충남이_아니면_제외() {
        assertThat(ParkingLotMapper.toEntity(item("서울특별시 중구 세종대로 110"))).isEmpty();
    }
}
