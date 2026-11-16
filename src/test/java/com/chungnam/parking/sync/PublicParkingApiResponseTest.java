package com.chungnam.parking.sync;

import com.chungnam.parking.domain.ChungnamRegion;
import com.chungnam.parking.domain.ParkingLot;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 공공데이터 API 응답 형식(2026-11 확인) 파싱 테스트
 */
class PublicParkingApiResponseTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    private static final String REAL_SHAPE = """
            {"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
             "body":{"items":{"item":[{
                "prkplceNo":"312-2-000001","prkplceNm":"테스트 공영주차장","prkplceSe":"공영","prkplceType":"노외",
                "rdnmadr":"","lnmadr":"충청남도 천안시 동남구 신부동 1-1","prkcmprt":"18","feedingSe":"1","enforceSe":"미시행",
                "operDay":"평일+토요일+공휴일","weekdayOperOpenHhmm":"00:00","weekdayOperColseHhmm":"23:59",
                "satOperOperOpenHhmm":"00:00","satOperCloseHhmm":"23:59","holidayOperOpenHhmm":"00:00","holidayCloseOpenHhmm":"23:59",
                "parkingchrgeInfo":"무료","basicTime":"30","basicCharge":"","addUnitTime":"","addUnitCharge":"",
                "dayCmmtktAdjTime":"","dayCmmtkt":"","monthCmmtkt":"","metpay":"","spcmnt":"",
                "institutionNm":"충청남도 천안시청","phoneNumber":"041-000-0000",
                "latitude":"36.8195","longitude":"127.1565","pwdbsPpkZoneYn":"",
                "referenceDate":"2026-04-20","insttCode":"4480000","insttNm":"충청남도 천안시"}]},
              "numOfRows":1,"pageNo":1,"totalCount":18994}}
            """;

    @Test
    void 실제_응답_형식_파싱_후_엔티티_변환() {
        PublicParkingApiResponse res = mapper.readValue(REAL_SHAPE, PublicParkingApiResponse.class);

        assertThat(res.header().isSuccess()).isTrue();
        assertThat(res.body().totalCount()).isEqualTo(18994);
        assertThat(res.body().itemList()).hasSize(1);

        // 도로명주소가 "" 이면 지번주소로 충남 판별
        ParkingLot lot = ParkingLotMapper.toEntity(res.body().itemList().get(0)).orElseThrow();
        assertThat(lot.getRegion()).isEqualTo(ChungnamRegion.CHEONAN);
        assertThat(lot.getRoadAddress()).isNull();
        assertThat(lot.getTotalSpaces()).isEqualTo(18);
        assertThat(lot.getFeeInfo().getBasicCharge()).isNull();
    }

    @Test
    void 항목이_1건이고_객체로_와도_파싱() {
        String single = REAL_SHAPE.replace("\"item\":[{", "\"item\":{").replace("}]},", "}},");
        PublicParkingApiResponse res = mapper.readValue(single, PublicParkingApiResponse.class);
        assertThat(res.body().itemList()).hasSize(1);
    }
}
