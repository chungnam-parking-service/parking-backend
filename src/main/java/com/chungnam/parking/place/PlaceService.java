package com.chungnam.parking.place;

import com.chungnam.parking.domain.ParkingLot;
import com.chungnam.parking.place.dto.PlaceDtos.*;
import com.chungnam.parking.repository.ParkingLotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 프론트 API.md 의 /places, /places/{placeId}/parking 응답을 조립한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceService {

    public static final int MAX_QUERY_LENGTH = 100;
    static final int NEARBY_RADIUS_METERS = 1000;
    static final int NEARBY_LIMIT = 10;
    private static final double METERS_PER_DEGREE_LAT = 111_320d;
    private static final ZoneOffset SEOUL = ZoneOffset.ofHours(9);

    private final PlaceRepository placeRepository;
    private final PlaceParkingLinkRepository linkRepository;
    private final ParkingLotRepository parkingLotRepository;

    /** GET /places?query= */
    public SearchPlacesResponse search(String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return new SearchPlacesResponse(List.of(), 0);   // 목업과 동일: 빈 검색은 빈 목록
        }
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("검색어는 " + MAX_QUERY_LENGTH + "자 이하로 입력해 주세요.");
        }
        List<PlaceDto> items = placeRepository.search(Place.normalize(trimmed)).stream()
                .map(PlaceService::toPlaceDto)
                .toList();
        return new SearchPlacesResponse(items, items.size());
    }

    /** GET /places/{placeId}/parking */
    public PlaceParkingResponse getParking(String placeId) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new PlaceNotFoundException(placeId));

        List<PlaceParkingLink> links = linkRepository.findByPlace_IdOrderBySortOrderAsc(placeId);

        List<PlaceParkingOption> primary = links.stream()
                .filter(l -> l.getRelationship().isPrimary())
                .map(this::toOption)
                .toList();

        // alternatives = 직접 등록한 주변 주차장 + (옵션) 공공데이터 주변 주차장
        List<PlaceParkingOption> alternatives = new ArrayList<>(links.stream()
                .filter(l -> !l.getRelationship().isPrimary())
                .map(this::toOption)
                .toList());

        if (place.isIncludePublicNearby()) {
            Set<Long> linkedLotIds = links.stream().map(l -> l.getParkingLot().getId()).collect(Collectors.toSet());
            publicNearby(place).stream()
                    .filter(lot -> !linkedLotIds.contains(lot.getId()))
                    .map(lot -> new PlaceParkingOption(toLotDto(lot), ParkingRelationship.NEARBY.value(),
                            null, null, null, List.of()))
                    .forEach(alternatives::add);
        }

        // 도보 시간 오름차순, 미확인(null)은 마지막. 정렬이 안정적이라 null 끼리는 등록 순서 → 거리 순 유지
        alternatives.sort(Comparator.comparing(PlaceParkingOption::walkingMinutes,
                Comparator.nullsLast(Comparator.naturalOrder())));

        return new PlaceParkingResponse(
                toPlaceDto(place),
                place.getHoursDescription(),
                place.getOperatingHoursWarning(),
                primary,
                alternatives,
                place.isDemo());
    }

    /** 목적지 좌표 기준 반경 내 공공데이터 주차장 (가까운 순) */
    private List<ParkingLot> publicNearby(Place place) {
        double lat = place.getLatitude();
        double lng = place.getLongitude();
        double dLat = NEARBY_RADIUS_METERS / METERS_PER_DEGREE_LAT;
        double dLng = NEARBY_RADIUS_METERS / (METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(lat)));

        List<ParkingLotRepository.NearbyRow> rows = parkingLotRepository.findNearby(
                lat, lng, lat - dLat, lat + dLat, lng - dLng, lng + dLng, NEARBY_RADIUS_METERS, NEARBY_LIMIT);
        Map<Long, ParkingLot> byId = parkingLotRepository.findAllById(
                        rows.stream().map(ParkingLotRepository.NearbyRow::getId).toList())
                .stream().collect(Collectors.toMap(ParkingLot::getId, Function.identity()));

        return rows.stream()
                .map(r -> byId.get(r.getId()))
                .filter(Objects::nonNull)
                .filter(lot -> lot.getLatitude() != null && lot.getLongitude() != null) // 입구 좌표 필수
                .toList();
    }

    private PlaceParkingOption toOption(PlaceParkingLink link) {
        return new PlaceParkingOption(
                toLotDto(link.getParkingLot()),
                link.getRelationship().value(),
                link.getBenefitDescription(),
                link.getValidationInstructions(),
                link.getWalkingMinutes(),
                List.copyOf(link.getWalkingSteps()));
    }

    static PlaceDto toPlaceDto(Place p) {
        return new PlaceDto(p.getId(), p.getName(), p.getCategory(), p.getAddress(),
                new Coordinates(p.getLatitude(), p.getLongitude()),
                p.getParkingStatus().value(), p.getParkingSummary());
    }

    static ParkingLotDto toLotDto(ParkingLot lot) {
        // 직접 등록 주차장은 등록한 외부 ID, 공공데이터 주차장은 "lot-{번호}"
        String id = lot.isManual() ? lot.getManagementNo() : "lot-" + lot.getId();

        Coordinates entrance = (lot.getEntranceLatitude() != null && lot.getEntranceLongitude() != null)
                ? new Coordinates(lot.getEntranceLatitude(), lot.getEntranceLongitude())
                : new Coordinates(lot.getLatitude(), lot.getLongitude()); // 공공데이터: 대표 좌표

        String fee = lot.getFeeDescription() != null ? lot.getFeeDescription()
                : lot.isManual() ? null : LotDescriptions.fee(lot.getFeeType(), lot.getFeeInfo());
        String hours = lot.getHoursDescription() != null ? lot.getHoursDescription()
                : lot.isManual() ? null : LotDescriptions.hours(lot.getOperatingHours());

        return new ParkingLotDto(
                id,
                lot.getName(),
                lot.getDisplayAddress(),
                entrance,
                lot.getEntrancePhotoUrl(),
                lot.getEntranceGuide(),
                fee,
                hours,
                lot.getVerifiedAt() == null ? null : lot.getVerifiedAt().atOffset(SEOUL));
    }
}
