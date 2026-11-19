package com.chungnam.parking.place;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * ai-cards.import-on-startup=true 이고 AI 매장 카드가 하나도 없으면, 서버가 뜬 뒤 백그라운드로 가져온다.
 * 배포 서버 첫 실행이나 H2(재시작마다 비워짐)에서 관리자 API 를 따로 부르지 않아도 되게 한다.
 * 이미 데이터가 있으면 아무것도 하지 않는다 (새 버전 반영은 POST /api/admin/places/import-cards).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlaceCardStartupImporter implements ApplicationRunner {

    private final PlaceCardService cardService;
    private final PlaceCardRepository cardRepository;

    @Value("${ai-cards.import-on-startup:false}")
    private boolean importOnStartup;

    @Override
    public void run(ApplicationArguments args) {
        if (!importOnStartup) {
            return;
        }
        if (cardRepository.count() > 0) {
            log.info("[AI 카드] 이미 {}곳이 있어 시작 시 가져오기를 건너뜁니다.", cardRepository.count());
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                cardService.importCards();
            } catch (RuntimeException e) {
                log.error("[AI 카드] 시작 시 가져오기 실패", e);
            }
        }, "ai-cards-import");
        worker.setDaemon(true);
        worker.start();
    }
}
