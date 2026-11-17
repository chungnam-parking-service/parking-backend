package com.chungnam.parking.place;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 파트가 만든 매장별 주차 카드 (음식점 약 3만9천 곳).
 * payload 는 프론트 계약 형식(PlaceParkingResponse)으로 변환을 마친 JSON 이고,
 * 나머지 칸은 검색용이다. 행은 PlaceCardService.importFrom 이 통째로 교체한다.
 */
@Entity
@Table(name = "place_card")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceCard {

    /** 프론트에 노출되는 목적지 ID (예: "sangga-MA010120220808573659") */
    @Id
    @Column(length = 100)
    private String id;

    /** 검색용 매장명: 한글·영문·숫자만 남기고 소문자 */
    @Column(name = "name_key", nullable = false, length = 200)
    private String nameKey;

    @Column(name = "category_key", nullable = false, length = 50)
    private String categoryKey;

    @Column(nullable = false, length = 300)
    private String address;

    /** 지역만 검색했을 때 정렬: available 0, unavailable 1, unknown 2 */
    @Column(name = "status_rank", nullable = false)
    private int statusRank;

    @Lob
    @Column(nullable = false)
    private String payload;
}
