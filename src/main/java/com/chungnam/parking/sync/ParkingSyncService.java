package com.chungnam.parking.sync;

import com.chungnam.parking.domain.ParkingLot;
import com.chungnam.parking.repository.ParkingLotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 공공데이터 → DB 동기화 (전국 데이터를 페이지 단위로 받아 충남만 upsert)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParkingSyncService {

    private final PublicParkingApiClient apiClient;
    private final PublicParkingProperties props;
    private final ParkingLotRepository repository;
    private final TransactionTemplate transactionTemplate;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public record SyncResult(int fetched, int chungnam, int inserted, int updated, long elapsedMs) {
    }

    public SyncResult syncAll() {
        if (!props.hasServiceKey()) {
            throw new IllegalStateException("PUBLIC_DATA_SERVICE_KEY 환경변수가 설정되지 않았습니다.");
        }
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("이미 동기화가 진행 중입니다.");
        }

        long start = System.currentTimeMillis();
        int fetched = 0, chungnam = 0, inserted = 0, updated = 0;
        try {
            int page = 1;
            int totalCount = Integer.MAX_VALUE;
            while ((long) (page - 1) * props.pageSize() < totalCount) {
                PublicParkingApiResponse.Body body = apiClient.fetchPage(page);
                if (body == null || body.itemList().isEmpty()) break;
                totalCount = body.totalCount() == null ? 0 : body.totalCount();

                fetched += body.itemList().size();
                List<ParkingLot> lots = body.itemList().stream()
                        .map(ParkingLotMapper::toEntity)
                        .flatMap(Optional::stream)
                        .toList();
                chungnam += lots.size();

                int[] r = transactionTemplate.execute(status -> upsert(lots));
                inserted += r[0];
                updated += r[1];

                log.info("[주차장 동기화] page={} fetched={} chungnam(누적)={}", page, fetched, chungnam);
                page++;
            }
        } finally {
            running.set(false);
        }

        SyncResult result = new SyncResult(fetched, chungnam, inserted, updated, System.currentTimeMillis() - start);
        log.info("[주차장 동기화 완료] {}", result);
        return result;
    }

    /** @return [inserted, updated] */
    private int[] upsert(List<ParkingLot> lots) {
        if (lots.isEmpty()) return new int[]{0, 0};

        // 같은 페이지 내 중복 관리번호는 마지막 것으로
        Map<String, ParkingLot> latest = lots.stream()
                .collect(Collectors.toMap(ParkingLot::getManagementNo, Function.identity(), (a, b) -> b, LinkedHashMap::new));

        Map<String, ParkingLot> existing = repository.findByManagementNoIn(latest.keySet()).stream()
                .collect(Collectors.toMap(ParkingLot::getManagementNo, Function.identity()));

        List<ParkingLot> toInsert = new ArrayList<>();
        int updated = 0;
        for (ParkingLot lot : latest.values()) {
            ParkingLot found = existing.get(lot.getManagementNo());
            if (found != null) {
                found.updateFrom(lot); // dirty checking 으로 UPDATE
                updated++;
            } else {
                toInsert.add(lot);
            }
        }
        repository.saveAll(toInsert);
        return new int[]{toInsert.size(), updated};
    }
}
