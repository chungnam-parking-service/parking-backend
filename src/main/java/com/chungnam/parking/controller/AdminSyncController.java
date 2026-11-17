package com.chungnam.parking.controller;

import com.chungnam.parking.common.ErrorResponse;
import com.chungnam.parking.sync.ParkingSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자용 수동 동기화
 * POST /api/admin/sync   (헤더 X-Admin-Key 필요)
 */
@io.swagger.v3.oas.annotations.tags.Tag(name = "관리자", description = "데이터 동기화 (프론트에서 사용하지 않음)")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminSyncController {

    private final ParkingSyncService syncService;

    @io.swagger.v3.oas.annotations.Operation(summary = "공공데이터 수동 동기화",
            description = "공공데이터포털에서 전국 주차장 데이터를 받아 충남만 DB 에 반영합니다. 우측 상단 Authorize 에 관리자 키를 넣고 실행하세요.",
            security = @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = com.chungnam.parking.config.OpenApiConfig.ADMIN_KEY))
    @PostMapping("/sync")
    public ResponseEntity<?> sync() {
        try {
            return ResponseEntity.ok(syncService.syncAll());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorResponse.of(409, "SYNC_UNAVAILABLE", e.getMessage()));
        }
    }
}
