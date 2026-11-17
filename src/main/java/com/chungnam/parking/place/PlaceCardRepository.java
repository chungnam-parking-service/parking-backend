package com.chungnam.parking.place;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlaceCardRepository extends JpaRepository<PlaceCard, String> {

    /** region: 주소에 포함될 시·군 이름(없으면 null), q: 정규화한 검색어(없으면 "") */
    String WHERE = " where (:region is null or c.address like concat('%', :region, '%'))"
            + " and (:q = '' or c.nameKey like concat('%', :q, '%') or c.categoryKey like concat('%', :q, '%'))";

    /** 매장명 완전 일치 → 앞부분 일치 → 포함 → 업종 일치 순, 같으면 주차 정보가 확인된 곳·짧은 이름 먼저 */
    @Query("select c from PlaceCard c" + WHERE + " order by"
            + " case when c.nameKey = :q then 0 when c.nameKey like concat(:q, '%') then 1"
            + " when c.nameKey like concat('%', :q, '%') then 2 else 3 end,"
            + " c.statusRank, length(c.nameKey), c.id")
    List<PlaceCard> search(@Param("region") String region, @Param("q") String q, Limit limit);

    @Query("select count(c) from PlaceCard c" + WHERE)
    long countSearch(@Param("region") String region, @Param("q") String q);
}
