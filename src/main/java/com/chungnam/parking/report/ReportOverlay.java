package com.chungnam.parking.report;

import com.chungnam.parking.place.dto.PlaceDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 승인된 제보를 목적지 주차 응답에 덧씌운다. AI 매장 카드든 직접 등록 목적지든 같은 방식으로 동작한다.
 *
 * <ul>
 *   <li>제보의 lotId 와 같은 주차장에만 적용. 비어 있는 칸은 원래 값을 유지</li>
 *   <li>입구 좌표를 보냈으면 entranceCoordinates 를 바꾸고 coordinateType = "entrance"</li>
 *   <li>verifiedAt = 마지막으로 승인한 시각 (팀이 확인한 시점)</li>
 *   <li>여러 제보가 있으면 나중에 승인한 것이 이긴다</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ReportOverlay {

    private static final ZoneOffset SEOUL = ZoneOffset.ofHours(9);

    private final ParkingReportRepository repository;
    private final PublicUrl publicUrl;

    public PlaceParkingResponse apply(PlaceParkingResponse response) {
        List<ParkingReport> approved = repository.findByPlaceIdAndStatusOrderByReviewedAtAscIdAsc(
                response.place().id(), ReportStatus.APPROVED);
        if (approved.isEmpty()) {
            return response;
        }
        Map<String, List<ParkingReport>> byLot = approved.stream()
                .filter(r -> r.getLotId() != null)
                .collect(Collectors.groupingBy(ParkingReport::getLotId));
        if (byLot.isEmpty()) {
            return response;
        }
        return new PlaceParkingResponse(
                response.place(),
                response.placeHoursDescription(),
                response.operatingHoursWarning(),
                response.primaryParking().stream().map(o -> overlay(o, byLot.get(o.lot().id()))).toList(),
                response.alternatives().stream().map(o -> overlay(o, byLot.get(o.lot().id()))).toList(),
                response.isDemo());
    }

    private PlaceParkingOption overlay(PlaceParkingOption option, List<ParkingReport> reports) {
        if (reports == null || reports.isEmpty()) {
            return option;
        }
        ParkingLotDto lot = option.lot();
        Coordinates entrance = lot.entranceCoordinates();
        String coordinateType = lot.coordinateType();
        String photo = lot.entrancePhotoUrl();
        String guide = lot.entranceGuide();
        String fee = lot.feeDescription();
        String hours = lot.hoursDescription();
        String benefit = option.benefitDescription();
        String validation = option.validationInstructions();
        List<String> steps = option.walkingSteps();
        java.time.OffsetDateTime verifiedAt = lot.verifiedAt();

        for (ParkingReport r : reports) {
            if (r.hasEntrance()) {
                entrance = new Coordinates(r.getEntranceLatitude(), r.getEntranceLongitude());
                coordinateType = "entrance";
            }
            if (r.getPhotoPath() != null) photo = publicUrl.of(r.getPhotoPath());
            if (r.getEntranceGuide() != null) guide = r.getEntranceGuide();
            if (r.getFeeDescription() != null) fee = r.getFeeDescription();
            if (r.getHoursDescription() != null) hours = r.getHoursDescription();
            if (r.getBenefitDescription() != null) benefit = r.getBenefitDescription();
            if (r.getValidationInstructions() != null) validation = r.getValidationInstructions();
            if (!r.walkingStepList().isEmpty()) steps = r.walkingStepList();
            verifiedAt = r.getReviewedAt().atOffset(SEOUL);
        }

        ParkingLotDto newLot = new ParkingLotDto(lot.id(), lot.name(), lot.address(), entrance, photo, guide,
                fee, hours, verifiedAt, coordinateType);
        return new PlaceParkingOption(newLot, option.relationship(), benefit, validation, option.walkingMinutes(),
                steps, option.distanceMeters(), option.estimatedWalkingMinutes());
    }
}
