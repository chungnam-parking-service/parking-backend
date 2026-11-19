-- AI 파트 매장 주차 카드 테이블 (운영 MySQL, 처음 한 번 실행).
-- prod 프로필은 ddl-auto: validate 라 테이블을 자동으로 만들지 않는다.
-- 만든 뒤 POST /api/admin/places/import-cards 로 데이터를 넣는다.
CREATE TABLE IF NOT EXISTS place_card (
  id           VARCHAR(100) NOT NULL,
  name_key     VARCHAR(200) NOT NULL,
  category_key VARCHAR(50)  NOT NULL,
  address      VARCHAR(300) NOT NULL,
  status_rank  INT          NOT NULL,
  payload      LONGTEXT     NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_place_card_search (name_key, category_key, address, status_rank)
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
