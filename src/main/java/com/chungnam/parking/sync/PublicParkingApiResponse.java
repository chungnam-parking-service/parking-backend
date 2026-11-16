package com.chungnam.parking.sync;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 공공데이터포털 전국주차장정보표준데이터 응답 (type=json)
 *
 * 실제 응답 형태:
 * {
 *   "header": {"resultCode":"00","resultMsg":"NORMAL SERVICE."},
 *   "body": {"items": {"item": [ {...}, ... ]}, "numOfRows":1000, "pageNo":1, "totalCount":18994}
 * }
 * item 의 값은 모두 문자열이며, 비어 있으면 "" 로 온다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PublicParkingApiResponse(Header header, Body body) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg) {
        public boolean isSuccess() {
            return "00".equals(resultCode);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items, Integer totalCount, Integer numOfRows, Integer pageNo) {
        public List<Item> itemList() {
            return (items == null || items.item() == null) ? List.of() : items.item();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(
            // 1건일 때 배열이 아닌 객체로 오는 경우 대비
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            List<Item> item) {
    }

    /**
     * 필드명은 공공데이터 원본 그대로 (오타 포함: weekdayOperColseHhmm, holidayCloseOpenHhmm)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String prkplceNo,            // 주차장관리번호
            String prkplceNm,            // 주차장명
            String prkplceSe,            // 주차장구분 (공영/민영)
            String prkplceType,          // 주차장유형 (노상/노외/부설)
            String rdnmadr,              // 소재지도로명주소
            String lnmadr,               // 소재지지번주소
            String prkcmprt,             // 주차구획수
            String operDay,              // 운영요일
            String weekdayOperOpenHhmm,
            String weekdayOperColseHhmm,
            String satOperOperOpenHhmm,
            String satOperCloseHhmm,
            String holidayOperOpenHhmm,
            String holidayCloseOpenHhmm,
            String parkingchrgeInfo,     // 요금정보 (무료/유료/혼합)
            String basicTime,
            String basicCharge,
            String addUnitTime,
            String addUnitCharge,
            String dayCmmtkt,            // 1일주차권요금
            String monthCmmtkt,          // 월정기권요금
            String metpay,               // 결제방법
            String spcmnt,               // 특기사항
            String institutionNm,        // 관리기관명
            String phoneNumber,
            String latitude,
            String longitude,
            String referenceDate
    ) {
    }
}
