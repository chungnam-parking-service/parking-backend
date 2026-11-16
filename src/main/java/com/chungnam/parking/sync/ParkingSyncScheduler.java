package com.chungnam.parking.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParkingSyncScheduler {

    private final ParkingSyncService syncService;
    private final PublicParkingProperties props;

    @Scheduled(cron = "${public-data.parking.sync-cron}", zone = "Asia/Seoul")
    public void syncDaily() {
        if (!props.syncEnabled()) {
            return;
        }
        try {
            syncService.syncAll();
        } catch (Exception e) {
            log.error("[주차장 동기화 실패]", e);
        }
    }
}
