package com.chungnam.parking.place;

import com.chungnam.parking.domain.ParkingLot;
import com.chungnam.parking.place.dto.PlaceAdminDtos.ManualLotRequest;
import com.chungnam.parking.place.dto.PlaceAdminDtos.ParkingLinkRequest;
import com.chungnam.parking.place.dto.PlaceAdminDtos.UpsertPlaceRequest;
import com.chungnam.parking.place.dto.PlaceDtos.PlaceParkingResponse;
import com.chungnam.parking.repository.ParkingLotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * 관리자용 목적지 등록. 목적지와 그 연결 주차장을 한 번에 만들거나(같은 ID 면) 통째로 교체한다.
 * 직접 등록한 주차장(manual)은 목적지 사이에 공유할 수 있고, 목적지를 지워도 남는다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PlaceAdminService {

    private static final ZoneOffset SEOUL = ZoneOffset.ofHours(9);

    private final PlaceRepository placeRepository;
    private final PlaceParkingLinkRepository linkRepository;
    private final ParkingLotRepository parkingLotRepository;
    private final PlaceService placeService;

    /** PUT: 없으면 생성, 있으면 덮어쓴다. 저장 결과를 프론트가 보게 될 응답 그대로 돌려준다. */
    public PlaceParkingResponse upsert(String placeId, UpsertPlaceRequest r) {
        Place latest = Place.builder()
                .id(placeId).name(r.name().trim()).category(r.category())
                .address(r.address().trim()).latitude(r.latitude()).longitude(r.longitude())
                .parkingStatus(PlaceParkingStatus.valueOf(r.parkingStatus().toUpperCase(Locale.ROOT)))
                .parkingSummary(r.parkingSummary()).hoursDescription(r.hoursDescription())
                .operatingHoursWarning(r.operatingHoursWarning())
                .demo(false).includePublicNearby(r.includePublicNearby())
                .build();
        Place place = placeRepository.findById(placeId).map(p -> {
            p.update(latest);
            return p;
        }).orElseGet(() -> placeRepository.save(latest));

        // 연결은 매번 통째로 교체 (순서는 요청 배열 순서)
        linkRepository.deleteAll(linkRepository.findByPlace_IdOrderBySortOrderAsc(placeId));
        linkRepository.flush();
        List<ParkingLinkRequest> requests = r.parking() == null ? List.of() : r.parking();
        linkRepository.saveAll(IntStream.range(0, requests.size())
                .mapToObj(i -> toLink(place, requests.get(i), i))
                .toList());

        return placeService.getParking(placeId);
    }

    public void delete(String placeId) {
        Place place = placeRepository.findById(placeId).orElseThrow(() -> new PlaceNotFoundException(placeId));
        linkRepository.deleteAll(linkRepository.findByPlace_IdOrderBySortOrderAsc(placeId));
        linkRepository.flush();
        placeRepository.delete(place);
    }

    private PlaceParkingLink toLink(Place place, ParkingLinkRequest q, int order) {
        return PlaceParkingLink.builder()
                .place(place)
                .parkingLot(resolveLot(q))
                .relationship(ParkingRelationship.valueOf(q.relationship().toUpperCase(Locale.ROOT)))
                .benefitDescription(q.benefitDescription())
                .validationInstructions(q.validationInstructions())
                .walkingMinutes(q.walkingMinutes())
                .walkingSteps(q.walkingSteps())
                .sortOrder(order)
                .build();
    }

    private ParkingLot resolveLot(ParkingLinkRequest q) {
        if ((q.lot() == null) == (q.existingLotId() == null)) {
            throw new IllegalArgumentException("parking 항목에는 lot 또는 existingLotId 중 하나만 보내 주세요.");
        }
        return q.lot() != null ? saveManualLot(q.lot()) : findPublicLot(q.existingLotId());
    }

    /** 공공데이터 주차장 연결. ID 는 응답에 보이는 "lot-138" 형식 */
    private ParkingLot findPublicLot(String lotId) {
        ParkingLot lot = parkingLotRepository.findById(Long.parseLong(lotId.substring("lot-".length())))
                .orElseThrow(() -> new IllegalArgumentException("공공데이터 주차장을 찾을 수 없습니다: " + lotId));
        if (lot.isManual()) {
            throw new IllegalArgumentException("직접 등록한 주차장은 existingLotId 가 아니라 lot 으로 보내 주세요: " + lotId);
        }
        return lot;
    }

    private ParkingLot saveManualLot(ManualLotRequest m) {
        if (m.id().matches("lot-\\d+")) {
            throw new IllegalArgumentException("주차장 id 가 공공데이터 형식(lot-숫자)과 같습니다: " + m.id());
        }
        ParkingLot candidate = ParkingLot.manual(m.id(), m.name().trim(), m.address().trim(),
                m.latitude(), m.longitude(), m.entrancePhotoUrl(), m.entranceGuide(),
                m.feeDescription(), m.hoursDescription(),
                m.verifiedAt() == null ? null : m.verifiedAt().withOffsetSameInstant(SEOUL).toLocalDateTime());
        return parkingLotRepository.findByManagementNo(m.id()).map(existing -> {
            if (!existing.isManual()) {
                throw new IllegalArgumentException("공공데이터 주차장 번호와 겹치는 id 입니다: " + m.id());
            }
            existing.updateManualFrom(candidate);
            return existing;
        }).orElseGet(() -> parkingLotRepository.save(candidate));
    }
}
