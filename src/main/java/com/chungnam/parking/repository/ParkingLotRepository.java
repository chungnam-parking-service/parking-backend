package com.chungnam.parking.repository;

import com.chungnam.parking.domain.ChungnamRegion;
import com.chungnam.parking.domain.ParkingLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ParkingLotRepository extends JpaRepository<ParkingLot, Long>, JpaSpecificationExecutor<ParkingLot> {

    Optional<ParkingLot> findByManagementNo(String managementNo);

    List<ParkingLot> findByManagementNoIn(Collection<String> managementNos);

    long countByRegion(ChungnamRegion region);

    /**
     * 반경 검색: 위경도 박스로 1차 필터 후 Haversine 공식으로 실제 거리(m)를 계산한다.
     * (MySQL / H2 모두 동작하는 표준 함수만 사용)
     */
    @Query(value = """
            SELECT t.id AS id, t.distance AS distance
            FROM (
                SELECT p.id,
                       6371000 * ACOS(LEAST(1.0,
                           COS(RADIANS(:lat)) * COS(RADIANS(p.latitude)) * COS(RADIANS(p.longitude) - RADIANS(:lng))
                         + SIN(RADIANS(:lat)) * SIN(RADIANS(p.latitude))
                       )) AS distance
                FROM parking_lot p
                WHERE p.latitude BETWEEN :minLat AND :maxLat
                  AND p.longitude BETWEEN :minLng AND :maxLng
            ) t
            WHERE t.distance <= :radius
            ORDER BY t.distance
            LIMIT :limit
            """, nativeQuery = true)
    List<NearbyRow> findNearby(@Param("lat") double lat,
                               @Param("lng") double lng,
                               @Param("minLat") double minLat,
                               @Param("maxLat") double maxLat,
                               @Param("minLng") double minLng,
                               @Param("maxLng") double maxLng,
                               @Param("radius") double radiusMeters,
                               @Param("limit") int limit);

    interface NearbyRow {
        Long getId();
        Double getDistance();
    }
}
