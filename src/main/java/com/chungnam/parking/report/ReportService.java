package com.chungnam.parking.report;

import com.chungnam.parking.common.ApiException;
import com.chungnam.parking.place.PlaceService;
import com.chungnam.parking.place.dto.PlaceDtos.Coordinates;
import com.chungnam.parking.report.ReportDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ReportService {

    /** 한 사람이 하루에 보낼 수 있는 제보 수 */
    static final int DAILY_LIMIT = 10;
    private static final ZoneOffset SEOUL = ZoneOffset.ofHours(9);

    private final ParkingReportRepository repository;
    private final PlaceService placeService;
    private final PhotoStorage photoStorage;
    private final PublicUrl publicUrl;
    private final Clock clock;

    @Transactional
    public ReportReceipt submit(String placeId, ReportRequest request, MultipartFile photo, String clientHash) {
        String lotId = blankToNull(request.lotId());
        if (lotId == null) {
            placeService.getParking(placeId);            // 404 PLACE_NOT_FOUND
        } else {
            placeService.requireLot(placeId, lotId);     // 404 PLACE_NOT_FOUND / LOT_NOT_FOUND
        }
        if ((request.entranceLatitude() == null) != (request.entranceLongitude() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "입구 위도와 경도는 함께 보내 주세요.");
        }
        List<String> steps = request.walkingSteps() == null ? List.of()
                : request.walkingSteps().stream()
                .map(s -> s == null ? "" : s.replaceAll("[\\r\\n]+", " ").trim())
                .filter(s -> !s.isEmpty())
                .toList();
        boolean hasPhoto = photo != null && !photo.isEmpty();
        boolean hasContent = hasPhoto || request.entranceLatitude() != null || !steps.isEmpty()
                || Stream.of(request.entranceGuide(), request.feeDescription(), request.hoursDescription(),
                request.benefitDescription(), request.validationInstructions(), request.comment())
                .anyMatch(s -> blankToNull(s) != null);
        if (!hasContent) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_REPORT", "알려 주실 내용을 하나 이상 입력해 주세요.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (repository.countByClientHashAndCreatedAtAfter(clientHash, now.minusHours(24)) >= DAILY_LIMIT) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REPORTS",
                    "오늘은 더 이상 보낼 수 없어요. 내일 다시 시도해 주세요.");
        }

        String photoPath = hasPhoto ? photoStorage.save(photo) : null;
        ParkingReport saved = repository.save(new ParkingReport(placeId, lotId,
                blankToNull(request.entranceGuide()), blankToNull(request.feeDescription()),
                blankToNull(request.hoursDescription()), blankToNull(request.benefitDescription()),
                blankToNull(request.validationInstructions()), steps,
                request.entranceLatitude(), request.entranceLongitude(), photoPath,
                blankToNull(request.comment()), clientHash, now));
        return new ReportReceipt(saved.getId(), saved.getStatus().value(), placeId, lotId,
                publicUrl.of(photoPath), now.atOffset(SEOUL));
    }

    @Transactional(readOnly = true)
    public List<ReportView> list(String status, int limit) {
        List<ParkingReport> reports = status == null || status.isBlank()
                ? repository.findAllByOrderByCreatedAtDesc(Limit.of(limit))
                : repository.findByStatusOrderByCreatedAtDesc(ReportStatus.from(status), Limit.of(limit));
        return reports.stream().map(this::view).toList();
    }

    @Transactional
    public ReportView approve(Long id, String note) {
        ParkingReport report = pending(id);
        report.approve(LocalDateTime.now(clock), blankToNull(note));
        return view(report);
    }

    @Transactional
    public ReportView reject(Long id, String note) {
        ParkingReport report = pending(id);
        String photo = report.getPhotoPath();
        report.reject(LocalDateTime.now(clock), blankToNull(note));
        photoStorage.delete(photo);
        return view(report);
    }

    private ParkingReport pending(Long id) {
        ParkingReport report = repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "제보를 찾을 수 없습니다."));
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REVIEWED", "이미 처리된 제보입니다.");
        }
        return report;
    }

    private ReportView view(ParkingReport r) {
        return new ReportView(r.getId(), r.getStatus().value(), r.getPlaceId(), r.getLotId(),
                r.getEntranceGuide(), r.getFeeDescription(), r.getHoursDescription(),
                r.getBenefitDescription(), r.getValidationInstructions(), r.walkingStepList(),
                r.hasEntrance() ? new Coordinates(r.getEntranceLatitude(), r.getEntranceLongitude()) : null,
                publicUrl.of(r.getPhotoPath()), r.getComment(),
                seoul(r.getCreatedAt()), seoul(r.getReviewedAt()), r.getReviewNote(), r.getLotId() != null);
    }

    private static OffsetDateTime seoul(LocalDateTime t) {
        return t == null ? null : t.atOffset(SEOUL);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
