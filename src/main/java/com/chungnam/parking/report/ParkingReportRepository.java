package com.chungnam.parking.report;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ParkingReportRepository extends JpaRepository<ParkingReport, Long> {

    long countByClientHashAndCreatedAtAfter(String clientHash, LocalDateTime after);

    /** 응답에 덧씌울 승인된 제보 (오래된 것부터 → 나중 승인이 이긴다) */
    List<ParkingReport> findByPlaceIdAndStatusOrderByReviewedAtAscIdAsc(String placeId, ReportStatus status);

    List<ParkingReport> findByStatusOrderByCreatedAtDesc(ReportStatus status, Limit limit);

    List<ParkingReport> findAllByOrderByCreatedAtDesc(Limit limit);
}
