package com.chungnam.parking.feedback;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 주차장 정보 정확도 피드백 ("입구가 맞아요 / 달라요").
 * 목적지와 주차장은 프론트에 보이는 문자열 ID 로 저장한다 (AI 매장 카드와 직접 등록 목적지 모두 같은 방식).
 */
@Entity
@Table(name = "parking_feedback", indexes = {
        @Index(name = "idx_feedback_place_lot", columnList = "place_id, lot_id"),
        @Index(name = "idx_feedback_client", columnList = "client_hash, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParkingFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "place_id", nullable = false, length = 100)
    private String placeId;

    @Column(name = "lot_id", nullable = false, length = 100)
    private String lotId;

    /** true = 정보가 맞음, false = 다름 */
    @Column(nullable = false)
    private boolean accurate;

    @Column(length = 300)
    private String comment;

    /** IP 의 해시 (ClientFingerprint). 원래 IP 는 저장하지 않는다 */
    @Column(name = "client_hash", nullable = false, length = 64)
    private String clientHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public ParkingFeedback(String placeId, String lotId, boolean accurate, String comment,
                           String clientHash, LocalDateTime createdAt) {
        this.placeId = placeId;
        this.lotId = lotId;
        this.accurate = accurate;
        this.comment = comment;
        this.clientHash = clientHash;
        this.createdAt = createdAt;
    }
}
