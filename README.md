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
`PUBLIC_DATA_SERVICE_KEY`(공공데이터포털 Decoding 키), `ADMIN_API_KEY`

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
| POST | `/api/admin/sync` | 공공데이터 수동 동기화 (헤더 `X-Admin-Key`) |

### 검색 파라미터 (`/api/parking-lots`)
- `keyword` 주차장명·주소, `region` (CHEONAN, ASAN, GONGJU …), `category` (PUBLIC/PRIVATE),
  `feeType` (FREE/PAID/MIXED), `minSpaces`, `openNow=true`
- `page`, `size`(기본 20), `sort=name,asc` / `sort=totalSpaces,desc`

### 주변 검색 (`/api/parking-lots/nearby`)
- `lat`, `lng` (필수, 충남 범위), `radius` 100~10000m (기본 1000), `limit` (기본 50), `feeType`

## 데이터
공공데이터포털 **전국주차장정보표준데이터** API에서 전국 데이터를 받아 주소가 충청남도인 것만 저장합니다.
`prod` 프로필에서는 매일 04:00 자동 동기화되며, 주차장관리번호 기준으로 upsert 합니다.
