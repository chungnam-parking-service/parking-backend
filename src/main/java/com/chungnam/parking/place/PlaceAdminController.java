package com.chungnam.parking.place;

import com.chungnam.parking.common.ErrorResponse;
import com.chungnam.parking.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "관리자", description = "데이터 관리 (프론트에서 사용하지 않음)")
@SecurityRequirement(name = OpenApiConfig.ADMIN_KEY)
@RestController
@RequestMapping("/api/admin/places")
@RequiredArgsConstructor
public class PlaceAdminController {

    private final PlaceCardService cardService;

    @Operation(summary = "AI 파트 매장 주차 데이터 가져오기",
            description = "AI_CARDS_URL(기본: AI 레포 Releases 의 place_parking_cn.jsonl.gz)을 받아 매장 카드를 통째로 교체합니다. "
                    + "프론트 계약 형식으로 변환해 저장하며, 실패하면 기존 데이터가 유지됩니다.")
    @PostMapping("/import-cards")
    public ResponseEntity<?> importCards() {
        try {
            return ResponseEntity.ok(cardService.importCards());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorResponse.of(409, "IMPORT_UNAVAILABLE", e.getMessage()));
        }
    }
}
