package com.chungnam.parking.feedback;

import com.chungnam.parking.common.ApiException;
import com.chungnam.parking.feedback.FeedbackDtos.*;
import com.chungnam.parking.place.PlaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    /** 같은 사람이 같은 주차장에 다시 보낼 수 있기까지의 시간 */
    static final int DUPLICATE_WINDOW_HOURS = 24;
    private static final ZoneOffset SEOUL = ZoneOffset.ofHours(9);

    private final ParkingFeedbackRepository repository;
    private final PlaceService placeService;
    private final Clock clock;

    @Transactional
    public FeedbackResponse submit(String placeId, String lotId, FeedbackRequest request, String clientHash) {
        placeService.requireLot(placeId, lotId);   // 404 PLACE_NOT_FOUND / LOT_NOT_FOUND

        LocalDateTime now = LocalDateTime.now(clock);
        if (repository.existsByClientHashAndPlaceIdAndLotIdAndCreatedAtAfter(
                clientHash, placeId, lotId, now.minusHours(DUPLICATE_WINDOW_HOURS))) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_FEEDBACK",
                    "이 주차장에는 이미 의견을 보내셨어요. 하루 뒤 다시 보낼 수 있어요.");
        }
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim();
        ParkingFeedback saved = repository.save(
                new ParkingFeedback(placeId, lotId, request.accurate(), comment, clientHash, now));
        return new FeedbackResponse(saved.getId(), placeId, lotId, saved.isAccurate(), saved.getCreatedAt().atOffset(SEOUL));
    }

    @Transactional(readOnly = true)
    public List<FeedbackSummary> summary(String placeId, int limit) {
        return repository.summarize(placeId, Limit.of(limit)).stream()
                .map(r -> new FeedbackSummary(r.getPlaceId(), r.getLotId(), r.getAccurateCount(), r.getInaccurateCount(),
                        r.getAccurateCount() + r.getInaccurateCount(), r.getLastAt().atOffset(SEOUL)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FeedbackComment> recentComments(int limit) {
        return repository.findByCommentIsNotNullOrderByCreatedAtDesc(Limit.of(limit)).stream()
                .map(f -> new FeedbackComment(f.getId(), f.getPlaceId(), f.getLotId(), f.isAccurate(), f.getComment(),
                        f.getCreatedAt().atOffset(SEOUL)))
                .toList();
    }

    static OffsetDateTime seoul(LocalDateTime time) {
        return time == null ? null : time.atOffset(SEOUL);
    }
}
