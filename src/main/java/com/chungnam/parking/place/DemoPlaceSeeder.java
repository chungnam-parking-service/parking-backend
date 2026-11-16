package com.chungnam.parking.place;

import com.chungnam.parking.domain.ParkingLot;
import com.chungnam.parking.repository.ParkingLotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 프론트 목업(src/mocks/data.ts)과 같은 ID·값의 시연 데이터를 넣는다.
 * 프론트가 목업 모드와 서버 모드를 바꿔 가며 결과가 같은지 비교할 수 있다.
 * 이미 있으면 건너뛰며, 운영(prod) 프로필에서는 실행하지 않는다.
 */
@Slf4j
@Component
@Profile("!prod")
@RequiredArgsConstructor
public class DemoPlaceSeeder implements ApplicationRunner {

    private final PlaceRepository placeRepository;
    private final PlaceParkingLinkRepository linkRepository;
    private final ParkingLotRepository parkingLotRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (placeRepository.existsById("place-demo-cafe")) {
            return;
        }

        // ----- 주차장 (직접 등록) -----
        ParkingLot cafeLot = lot(ParkingLot.manual("lot-demo-cafe", "모퉁이 빌딩 주차장", "충남 천안시 불당동 · 예시 주소",
                36.8148, 127.1082, null,
                "매장 정문 반대편, 건물 오른쪽 골목의 지하 입구로 들어가세요.",
                "무료 시간 초과 시 10분당 500원", "매일 09:00–21:00",
                LocalDateTime.of(2026, 11, 7, 9, 0)));

        ParkingLot publicLot = lot(ParkingLot.manual("lot-demo-public", "가온 공영주차장", "충남 천안시 불당동 · 예시 주소",
                36.8152, 127.1078, null,
                "큰길에서 파란 P 표지판을 따라 진입하세요.",
                "최초 30분 500원 · 이후 10분당 200원", "매일 08:00–23:00", null));

        ParkingLot onyangLot = lot(ParkingLot.manual("lot-demo-onyang", "온양 마을주차장", "충남 아산시 온천동 · 예시 주소",
                36.7807, 127.003, null,
                "큰길에서 파란 P 표지판을 따라 진입하세요.",
                "30분 600원 · 이후 10분당 200원", "24시간", null));

        // ----- 목적지 -----
        Place cafe = placeRepository.save(Place.builder()
                .id("place-demo-cafe").name("모퉁이 커피").category("카페")
                .address("충남 천안시 불당동 · 예시 매장").latitude(36.815).longitude(127.1085)
                .parkingStatus(PlaceParkingStatus.AVAILABLE).parkingSummary("건물 주차 가능 · 음료 구매 시 2시간 무료")
                .hoursDescription("매일 10:00–22:00")
                .operatingHoursWarning("주차장은 21:00에 닫혀요. 매장 마감보다 1시간 빠르니 이용 시간을 확인해 주세요.")
                .demo(true).includePublicNearby(false)
                .build());

        Place books = placeRepository.save(Place.builder()
                .id("place-demo-books").name("온양 작은책방").category("서점")
                .address("충남 아산시 온천동 · 예시 매장").latitude(36.7803).longitude(127.0022)
                .parkingStatus(PlaceParkingStatus.UNAVAILABLE).parkingSummary("매장 주차 불가 · 주변 주차장 확인")
                .hoursDescription("화–일 11:00–20:00 · 월요일 휴무")
                .demo(true).includePublicNearby(false)
                .build());

        placeRepository.save(Place.builder()
                .id("place-demo-food").name("공주 느린식탁").category("음식점")
                .address("충남 공주시 중동 · 예시 매장").latitude(36.4544).longitude(127.1248)
                .parkingStatus(PlaceParkingStatus.UNKNOWN).parkingSummary("주차 정보 없음 · 방문 전 확인 필요")
                .demo(true).includePublicNearby(false)
                .build());

        // ----- 목적지 ↔ 주차장 -----
        linkRepository.saveAll(List.of(
                PlaceParkingLink.builder()
                        .place(cafe).parkingLot(cafeLot).relationship(ParkingRelationship.SHARED)
                        .benefitDescription("음료 1잔 이상 구매 시 2시간 무료")
                        .validationInstructions("주문할 때 카운터에 차량번호 뒤 4자리를 알려주세요. 출차 전 등록 여부를 확인하세요.")
                        .walkingMinutes(2)
                        .walkingSteps(List.of("B1 주차 후 중앙 엘리베이터로 이동하세요.",
                                "엘리베이터를 타고 1층에서 내려주세요.",
                                "건물 정문으로 나와 왼쪽 모퉁이 매장으로 들어가세요."))
                        .sortOrder(0).build(),
                PlaceParkingLink.builder()
                        .place(cafe).parkingLot(publicLot).relationship(ParkingRelationship.NEARBY)
                        .validationInstructions("출차 전 1층 무인 정산기에서 결제하세요.")
                        .walkingMinutes(5)
                        .walkingSteps(List.of("주차장 보행자 출구로 나와주세요.",
                                "큰길을 따라 직진한 뒤 횡단보도를 건너세요.",
                                "오른쪽 골목의 목적지 정문으로 들어가세요."))
                        .sortOrder(1).build(),
                PlaceParkingLink.builder()
                        .place(books).parkingLot(onyangLot).relationship(ParkingRelationship.NEARBY)
                        .validationInstructions("출차 전 1층 무인 정산기에서 결제하세요.")
                        .walkingMinutes(3)
                        .walkingSteps(List.of("보행자 출구로 나와 시장 방향으로 걸어주세요.",
                                "첫 번째 골목에서 왼쪽으로 이동하세요.",
                                "책방 간판이 있는 1층으로 들어가세요."))
                        .sortOrder(0).build()
        ));

        log.info("[시연 데이터] 프론트 목업과 같은 목적지 3곳을 등록했습니다.");
    }

    private ParkingLot lot(ParkingLot candidate) {
        return parkingLotRepository.findByManagementNo(candidate.getManagementNo())
                .orElseGet(() -> parkingLotRepository.save(candidate));
    }
}
