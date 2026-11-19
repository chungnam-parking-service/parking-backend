# 충남 주차정보 검색 플랫폼 - 백엔드

Spring Boot 4.1 · Java 17 · JPA · H2(로컬) / MySQL(운영)

## 실행

로컬 실행 (H2 + 샘플 데이터 8건, http://localhost:8080):

```bash
./gradlew bootRun
```

테스트:

```bash
./gradlew test
```

IntelliJ에서는 `ParkingApplication.java`의 ▶ 버튼으로 실행하거나, Gradle 탭에서 `application → bootRun`을 실행하세요.

운영(MySQL) 실행 시 환경변수: `SPRING_PROFILES_ACTIVE=prod`, `DB_HOST`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`,
`PUBLIC_DATA_SERVICE_KEY`(공공데이터포털 Decoding 키), `ADMIN_API_KEY`, `AI_CARDS_URL`(선택, 기본은 AI 레포 Releases 데이터),
`AI_ASSISTANT_URL`(선택, AI 파트 주차 비서 서버 주소. 비우면 `/assistant/chat`이 503)

### AI 파트 매장 데이터 (음식점 약 3만9천 곳)
프론트 검색·상세(`/places`)에 쓰는 매장 데이터는 AI 레포 Releases에서 가져옵니다. 서버를 켠 뒤 한 번 실행하세요(약 20초).

```bash
curl -X POST -H "X-Admin-Key: $ADMIN_API_KEY" http://localhost:8080/api/admin/places/import-cards
```

로컬(H2)은 서버를 껐다 켜면 비워지니 다시 실행합니다. 운영 MySQL은 처음 한 번 `db/place_card.sql`로 테이블을 만든 뒤 실행합니다.
변환 규칙은 `docs/API_SPEC.md` 2-1장에 있습니다.

### AI 주차 비서 중계
`POST /assistant/chat`은 질문을 AI 레포의 주차 비서 서버로 넘기고 답을 그대로 돌려줍니다. 프론트는 이 백엔드만 부르면 됩니다.
AI 서버는 AI 레포에서 `uvicorn server.app:app --port 8000`으로 띄우고, 백엔드에 `AI_ASSISTANT_URL=http://localhost:8000`을 줍니다.

```bash
curl -X POST http://localhost:8080/assistant/chat -H 'Content-Type: application/json' \
  -d '{"message": "천안 직산 배나무숲 주차 돼요?"}'
```

## API

서버 실행 후 **http://localhost:8080/swagger-ui.html** 에서 전체 API 문서를 보고 브라우저에서 바로 호출해 볼 수 있습니다.
(OpenAPI JSON: `/v3/api-docs`)

| Method | URL | 설명 |
|---|---|---|
| GET | `/api/parking-lots` | 조건 검색 (페이징) |
| GET | `/api/parking-lots/nearby` | 내 주변 반경 검색 (거리순) |
| GET | `/api/parking-lots/{id}` | 상세 |
| GET | `/api/parking-lots/{id}/fee?minutes=90` | 예상 요금 |
| GET | `/api/regions` | 충남 15개 시·군 + 주차장 수 |
| GET | `/places?query=천안&limit=20` | 목적지 검색 (직접 등록 + AI 매장) |
| GET | `/places/{placeId}/parking` | 목적지 주차 안내 |
| POST | `/assistant/chat` | 주차 비서에게 묻기 (AI 서버 중계, 4–16초) |
| POST | `/api/admin/sync` | 공공데이터 수동 동기화 (헤더 `X-Admin-Key`) |
| POST | `/api/admin/places/import-cards` | AI 매장 데이터 가져오기 (헤더 `X-Admin-Key`) |

### 검색 파라미터 (`/api/parking-lots`)
- `keyword` 주차장명·주소, `region` (CHEONAN, ASAN, GONGJU …), `category` (PUBLIC/PRIVATE),
  `feeType` (FREE/PAID/MIXED), `minSpaces`, `openNow=true`
- `page`, `size`(기본 20), `sort=name,asc` / `sort=totalSpaces,desc`

### 주변 검색 (`/api/parking-lots/nearby`)
- `lat`, `lng` (필수, 충남 범위), `radius` 100~10000m (기본 1000), `limit` (기본 50), `feeType`

## 데이터
공공데이터포털 **전국주차장정보표준데이터** API에서 전국 데이터를 받아 주소가 충청남도인 것만 저장합니다.
`prod` 프로필에서는 매일 04:00 자동 동기화되며, 주차장관리번호 기준으로 upsert 합니다.
