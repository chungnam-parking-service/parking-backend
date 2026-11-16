package com.chungnam.parking.service;

import com.chungnam.parking.common.NotFoundException;
import com.chungnam.parking.domain.ChungnamRegion;
import com.chungnam.parking.domain.FeeType;
import com.chungnam.parking.domain.ParkingLot;
import com.chungnam.parking.dto.*;
import com.chungnam.parking.repository.ParkingLotRepository;
import com.chungnam.parking.repository.ParkingLotSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParkingLotService {

    private static final double METERS_PER_DEGREE_LAT = 111_320d;
    public static final int MAX_RADIUS_METERS = 10_000;
    public static final int MAX_NEARBY_LIMIT = 200;

    private final ParkingLotRepository repository;
    private final Clock clock;

    /** 조건 검색 (페이징) */
    public PageResponse<ParkingLotSummary> search(ParkingSearchCondition cond, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now(clock);
        Specification<ParkingLot> spec = ParkingLotSpecs.of(cond);

        if (!Boolean.TRUE.equals(cond.openNow())) {
            Page<ParkingLotSummary> page = repository.findAll(spec, pageable)
                    .map(p -> ParkingLotSummary.of(p, now));
            return PageResponse.of(page);
        }

        // "현재 운영 중" 은 운영시간 문자열 기반이라 DB 에서 걸러내기 어려움 → 메모리 필터 후 수동 페이징
        // (충남 데이터는 수천 건 수준이라 충분히 감당 가능)
        List<ParkingLotSummary> filtered = repository.findAll(spec, pageable.getSort()).stream()
                .filter(p -> p.getOperatingHours() != null
                        && Boolean.TRUE.equals(p.getOperatingHours().isOpenAt(now)))
                .map(p -> ParkingLotSummary.of(p, now))
                .toList();

        int from = (int) Math.min(pageable.getOffset(), filtered.size());
        int to = Math.min(from + pageable.getPageSize(), filtered.size());
        return PageResponse.of(new PageImpl<>(filtered.subList(from, to), pageable, filtered.size()));
    }

    /** 내 위치 기준 반경 검색 (거리순) */
    public List<ParkingLotSummary> findNearby(double lat, double lng, int radiusMeters, int limit, FeeType feeType) {
        int radius = Math.min(Math.max(radiusMeters, 100), MAX_RADIUS_METERS);
        int size = Math.min(Math.max(limit, 1), MAX_NEARBY_LIMIT);

        double dLat = radius / METERS_PER_DEGREE_LAT;
        double dLng = radius / (METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(lat)));

        List<ParkingLotRepository.NearbyRow> rows = repository.findNearby(
                lat, lng, lat - dLat, lat + dLat, lng - dLng, lng + dLng, radius, size);

        Map<Long, ParkingLot> lots = repository.findAllById(
                        rows.stream().map(ParkingLotRepository.NearbyRow::getId).toList())
                .stream().collect(Collectors.toMap(ParkingLot::getId, Function.identity()));

        LocalDateTime now = LocalDateTime.now(clock);
        return rows.stream()
                .map(r -> {
                    ParkingLot p = lots.get(r.getId());
                    return p == null ? null : ParkingLotSummary.of(p, now, r.getDistance());
                })
                .filter(s -> s != null && (feeType == null || lots.get(s.id()).getFeeType() == feeType))
                .toList();
    }

    public ParkingLotDetail getDetail(Long id) {
        return ParkingLotDetail.of(getOrThrow(id), LocalDateTime.now(clock));
    }

    /** 예상 주차요금 계산 */
    public FeeEstimateResponse estimateFee(Long id, int minutes) {
        ParkingLot p = getOrThrow(id);
        if (p.getFeeType() == FeeType.FREE) {
            return new FeeEstimateResponse(id, p.getName(), minutes, p.getFeeType().getLabel(), 0, "무료 주차장입니다.");
        }
        Integer fee = p.getFeeInfo() == null ? null : p.getFeeInfo().estimate(minutes);
        String message = fee == null ? "요금 정보가 없어 계산할 수 없습니다." : "공공데이터 기준 예상 요금이며 실제와 다를 수 있습니다.";
        return new FeeEstimateResponse(id, p.getName(), minutes, p.getFeeType().getLabel(), fee, message);
    }

    /** 시·군 목록 + 주차장 수 */
    public List<RegionStat> getRegionStats() {
        return Arrays.stream(ChungnamRegion.values())
                .map(r -> new RegionStat(r.name(), r.getLabel(), repository.countByRegion(r)))
                .toList();
    }

    private ParkingLot getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("주차장을 찾을 수 없습니다. id=" + id));
    }
}
