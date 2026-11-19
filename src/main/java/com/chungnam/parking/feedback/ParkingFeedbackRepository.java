package com.chungnam.parking.feedback;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ParkingFeedbackRepository extends JpaRepository<ParkingFeedback, Long> {

    boolean existsByClientHashAndPlaceIdAndLotIdAndCreatedAtAfter(
            String clientHash, String placeId, String lotId, LocalDateTime after);

    /** 주차장별 집계: [placeId, lotId, 맞아요 수, 달라요 수, 마지막 시각] */
    @Query("select f.placeId as placeId, f.lotId as lotId,"
            + " sum(case when f.accurate = true then 1 else 0 end) as accurateCount,"
            + " sum(case when f.accurate = false then 1 else 0 end) as inaccurateCount,"
            + " max(f.createdAt) as lastAt"
            + " from ParkingFeedback f"
            + " where (:placeId is null or f.placeId = :placeId)"
            + " group by f.placeId, f.lotId"
            + " order by count(f) desc, max(f.createdAt) desc")
    List<SummaryRow> summarize(@Param("placeId") String placeId, Limit limit);

    List<ParkingFeedback> findByCommentIsNotNullOrderByCreatedAtDesc(Limit limit);

    interface SummaryRow {
        String getPlaceId();

        String getLotId();

        Long getAccurateCount();

        Long getInaccurateCount();

        LocalDateTime getLastAt();
    }
}
