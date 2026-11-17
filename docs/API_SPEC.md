# 충남 주차 안내 백엔드 API 명세서 v1.0

프론트 [`docs/API.md` 주차 안내 API 초안 v0.1]을 기준으로 **실제 구현된 백엔드 API만** 정리한 최종 명세입니다.
목적지 API는 프론트 초안의 경로·필드·null 규칙을 그대로 따르며, 응답 타입은 프론트 `src/domain/parking.ts`와 1:1로 대응합니다.
아래 예시는 모두 실제 서버 응답입니다. 시연 목적지 응답은 프론트 `src/mocks/data.ts`와 완전히 같은지 자동 테스트(`PlaceApiTest`)로 확인합니다.

| 그룹 | 경로 | 사용처 |
| --- | --- | --- |
| **목적지 (프론트 API.md 계약)** | `GET /places`, `GET /places/{placeId}/parking` | 프론트 검색·상세 화면 |
| 주차장 조회 | `GET /api/parking-lots…`, `GET /api/regions` | 지도·주차장 목록 등 보조 화면 (선택) |
| 관리자 | `POST /api/admin/sync` | 백엔드 데이터 관리 전용 |

---

## 0. 공통

### Base URL
| 환경 | 값 |
| --- | --- |
| 로컬 | `http://localhost:8080` |
| 배포 | 미정 |

프론트 `.env`: `VITE_API_BASE_URL=http://localhost:8080`
API 문서·테스트 화면: `http://localhost:8080/swagger-ui.html`

### 규칙
- 인증 없이 조회합니다. 관리자 API만 `X-Admin-Key` 헤더가 필요합니다.
- 응답은 `application/json`, UTF-8.
- 목적지 API의 **ID는 모두 문자열**입니다. (`place-demo-cafe`, `lot-demo-cafe`, `lot-138`)
- 좌표는 WGS84 십진수이며 `{ "latitude": number, "longitude": number }` 객체로 보냅니다.
- 날짜시각은 ISO 8601, 서울 시간대 (`2026-11-07T09:00:00+09:00`).
- **모르는 값은 `null`, 목록은 `[]`** 로 보냅니다. 필드를 생략하거나 0분·0원·"무료"로 대체하지 않습니다.

### CORS
허용 출처: `http://localhost:5173`(Vite), `http://localhost:3000`. 배포 출처는 정해지면 추가합니다.
허용 메서드: `/places/**` GET, `/api/**` GET·POST.

### 오류 body
```json
{
  "status": 404,
  "code": "PLACE_NOT_FOUND",
  "message": "목적지를 찾을 수 없습니다.",
  "timestamp": "2026-11-16T22:40:42.219"
}
```
- 프론트 초안의 `{ code, message }`를 포함하고 `status`, `timestamp`가 더 붙습니다.
- **`message`는 디버깅용**입니다. 입력 오류에 따라 내부 문구가 들어갈 수 있으므로 화면에는 HTTP 상태 기준 문구를 표시해 주세요 (현재 프론트 어댑터 방식과 동일).

---

## 1. 목적지 검색

`GET /places?query=천안`

| 파라미터 | 필수 | 규칙 |
| --- | --- | --- |
| `query` | | 앞뒤 공백 제거 후 1–100자. **없거나 공백뿐이면 200 + 빈 목록**, 100자 초과는 400 |

- 이름 + 주소 + 카테고리를 대상으로, 공백을 무시하고 대소문자 구분 없이 부분 일치 검색합니다. (`모퉁이커피` → "모퉁이 커피", `카페` → 카테고리 일치)
- 페이지네이션 없이 전체 결과를 **이름순**으로 반환합니다. `total`은 `items` 개수입니다.
- 검색 대상은 백엔드에 등록된 목적지입니다. (외부 장소 검색 연동은 7장 참고)

### 200 — `SearchPlacesResponse`
```json
{
  "items": [
    {
      "id": "place-demo-cafe",
      "name": "모퉁이 커피",
      "category": "카페",
      "address": "충남 천안시 불당동 · 예시 매장",
      "coordinates": { "latitude": 36.815, "longitude": 127.1085 },
      "parkingStatus": "available",
      "parkingSummary": "건물 주차 가능 · 음료 구매 시 2시간 무료"
    }
  ],
  "total": 1
}
```

### `Place`
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | string | 목적지 ID |
| `name` | string | 이름 |
| `category` | string | 카페·음식점 등. 미등록이면 `""` |
| `address` | string | 주소 |
| `coordinates` | Coordinates | 목적지 좌표 |
| `parkingStatus` | `available` \| `unavailable` \| `unknown` | 아래 표 |
| `parkingSummary` | string | 한 줄 주차 요약 |

| `parkingStatus` | 의미 |
| --- | --- |
| `available` | 목적지에서 이용 가능한 주차 시설이 확인됨. **실시간 빈자리 보장 아님** |
| `unavailable` | 목적지 자체·연결 주차가 불가한 것으로 확인됨. 주변 주차장은 별도로 있을 수 있음 |
| `unknown` | 주차 가능 여부를 모름. 불가로 바꾸지 않음 |

### 오류
| HTTP | code | 언제 |
| --- | --- | --- |
| 400 | `BAD_REQUEST` | 검색어 100자 초과 |

---

## 2. 목적지 주차 상세

`GET /places/{placeId}/parking`

`primaryParking`에는 전용·공용·제휴 주차장, `alternatives`에는 주변 주차장이 들어갑니다. 둘 다 같은 `PlaceParkingOption` 구조이며 없으면 `[]`입니다.

### 200 — `PlaceParkingResponse` (`place-demo-cafe`)
```json
{
  "place": {
    "id": "place-demo-cafe",
    "name": "모퉁이 커피",
    "category": "카페",
    "address": "충남 천안시 불당동 · 예시 매장",
    "coordinates": { "latitude": 36.815, "longitude": 127.1085 },
    "parkingStatus": "available",
    "parkingSummary": "건물 주차 가능 · 음료 구매 시 2시간 무료"
  },
  "placeHoursDescription": "매일 10:00–22:00",
  "operatingHoursWarning": "주차장은 21:00에 닫혀요. 매장 마감보다 1시간 빠르니 이용 시간을 확인해 주세요.",
  "primaryParking": [
    {
      "lot": {
        "id": "lot-demo-cafe",
        "name": "모퉁이 빌딩 주차장",
        "address": "충남 천안시 불당동 · 예시 주소",
        "entranceCoordinates": { "latitude": 36.8148, "longitude": 127.1082 },
        "entrancePhotoUrl": null,
        "entranceGuide": "매장 정문 반대편, 건물 오른쪽 골목의 지하 입구로 들어가세요.",
        "feeDescription": "무료 시간 초과 시 10분당 500원",
        "hoursDescription": "매일 09:00–21:00",
        "verifiedAt": "2026-11-07T09:00:00+09:00",
        "coordinateType": "entrance"
      },
      "relationship": "shared",
      "benefitDescription": "음료 1잔 이상 구매 시 2시간 무료",
      "validationInstructions": "주문할 때 카운터에 차량번호 뒤 4자리를 알려주세요. 출차 전 등록 여부를 확인하세요.",
      "walkingMinutes": 2,
      "walkingSteps": [
        "B1 주차 후 중앙 엘리베이터로 이동하세요.",
        "엘리베이터를 타고 1층에서 내려주세요.",
        "건물 정문으로 나와 왼쪽 모퉁이 매장으로 들어가세요."
      ]
    }
  ],
  "alternatives": [
    {
      "lot": {
        "id": "lot-demo-public",
        "name": "가온 공영주차장",
        "address": "충남 천안시 불당동 · 예시 주소",
        "entranceCoordinates": { "latitude": 36.8152, "longitude": 127.1078 },
        "entrancePhotoUrl": null,
        "entranceGuide": "큰길에서 파란 P 표지판을 따라 진입하세요.",
        "feeDescription": "최초 30분 500원 · 이후 10분당 200원",
        "hoursDescription": "매일 08:00–23:00",
        "verifiedAt": null,
        "coordinateType": "entrance"
      },
      "relationship": "nearby",
      "benefitDescription": null,
      "validationInstructions": "출차 전 1층 무인 정산기에서 결제하세요.",
      "walkingMinutes": 5,
      "walkingSteps": [
        "주차장 보행자 출구로 나와주세요.",
        "큰길을 따라 직진한 뒤 횡단보도를 건너세요.",
        "오른쪽 골목의 목적지 정문으로 들어가세요."
      ]
    }
  ],
  "isDemo": true
}
```

### 공공데이터 주변 주차장이 들어간 `alternatives` 항목 예시
목적지에 공공데이터 자동 추천이 켜져 있으면 아래 형태의 항목이 추가됩니다.
```json
{
  "lot": {
    "id": "lot-1",
    "name": "천안종합터미널 공영주차장",
    "address": "충청남도 천안시 동남구 신부동 354-1",
    "entranceCoordinates": { "latitude": 36.8195, "longitude": 127.1565 },
    "entrancePhotoUrl": null,
    "entranceGuide": null,
    "feeDescription": "기본 30분 1,000원 · 추가 10분당 300원 · 1일 10,000원",
    "hoursDescription": "24시간",
    "verifiedAt": null,
    "coordinateType": "representative"
  },
  "relationship": "nearby",
  "benefitDescription": null,
  "validationInstructions": null,
  "walkingMinutes": null,
  "walkingSteps": []
}
```

### 필드 규칙

**최상위**
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `place` | Place | 검색 응답과 같은 구조 |
| `placeHoursDescription` | string \| null | 목적지 운영시간 |
| `operatingHoursWarning` | string \| null | 연결 주차장 운영시간 경고 (등록된 문구) |
| `primaryParking` | PlaceParkingOption[] | 전용·공용·제휴. 없어도 `[]` |
| `alternatives` | PlaceParkingOption[] | 주변 주차장. 없어도 `[]` |
| `isDemo` | boolean | 예시 데이터면 `true`, 실제 데이터는 `false` |

**`PlaceParkingOption`**
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `lot` | ParkingLot | 주차장 |
| `relationship` | `dedicated` \| `shared` \| `partner` \| `nearby` | 전용 / 건물 공용 / 제휴 / 주변. `alternatives`는 항상 `nearby` |
| `benefitDescription` | string \| null | 이 목적지에서 적용되는 할인 조건 |
| `validationInstructions` | string \| null | 주차 등록 또는 정산 안내 |
| `walkingMinutes` | number \| null | 주차장 → 목적지 도보 분. 등록값만 사용하며 **직선거리로 만들지 않음** |
| `walkingSteps` | string[] | 순서대로 표시할 도보 안내. 미확인이면 `[]` |

**`ParkingLot`**
| 필드 | 타입 | 직접 등록 주차장 | 공공데이터 주차장 |
| --- | --- | --- | --- |
| `id` | string | 등록 ID (`lot-demo-cafe`) | `lot-{번호}` (`lot-138`) |
| `name` | string | 등록값 | 공공데이터 주차장명 |
| `address` | string | 등록값 | 도로명 우선, 없으면 지번 |
| `entranceCoordinates` | Coordinates | **실제 입구 좌표** | **주차장 대표 좌표 (입구와 다를 수 있음)** |
| `entrancePhotoUrl` | string \| null | 등록된 공개 URL | `null` |
| `entranceGuide` | string \| null | 등록값 | `null` |
| `feeDescription` | string \| null | 등록값 | 공공데이터로 생성. 무료는 `"무료"`, 정보 없음은 `null` |
| `hoursDescription` | string \| null | 등록값 | 공공데이터로 생성. `"24시간"`, `"매일 09:00–21:00"`, `"평일 09:00–20:00 · 토 09:00–18:00"`, 정보 없음은 `null` |
| `verifiedAt` | string \| null | 팀이 마지막으로 확인한 시각 | `null` |
| `coordinateType` | string | `entrance` (실제 입구 좌표) | `representative` (주차장 대표 좌표, 입구와 다를 수 있음) |

### `alternatives` 구성과 정렬
1. 팀이 직접 등록한 주변 주차장 (도보 시간·안내 포함)
2. 공공데이터 자동 추천이 켜진 목적지는 **목적지 반경 1km 공공데이터 주차장을 가까운 순으로 최대 10곳** 추가. 이미 연결된 주차장과 좌표 없는 주차장은 제외
3. 전체를 `walkingMinutes` **오름차순, `null`은 마지막**으로 정렬. `null`끼리는 등록 순서 → 가까운 순서를 유지

### 오류
| HTTP | code | 언제 |
| --- | --- | --- |
| 404 | `PLACE_NOT_FOUND` | 없는 `placeId`. message `"목적지를 찾을 수 없습니다."` |

### 시연 데이터
서버를 켜면(운영 프로필 제외) 프론트 목업과 같은 ID·값의 목적지 3곳이 자동 등록됩니다. 시연 목적지는 목업과 같게 하기 위해 공공데이터 자동 추천을 끄고 있습니다.

| placeId | parkingStatus | 확인할 화면 상태 |
| --- | --- | --- |
| `place-demo-cafe` | `available` | 연결 주차장 + 주변 주차장, 운영시간 경고 |
| `place-demo-books` | `unavailable` | 연결 없음 → 주변 주차장 1곳 |
| `place-demo-food` | `unknown` | 연결·주변 모두 `[]` → 출발 버튼 없음 |

---

## 3. 오류와 네트워크 (프론트 초안 대응)

| HTTP | 백엔드 code | 의미 | 화면 동작 (프론트 초안) |
| --- | --- | --- | --- |
| 200 + 빈 `items` | - | 검색 결과 없음 | 빈 결과 안내 |
| 400 | `BAD_REQUEST`, `INVALID_PARAMETER`, `INVALID_SORT` | 유효하지 않은 입력 | 오류와 재시도 |
| 404 | `PLACE_NOT_FOUND` | 존재하지 않는 목적지 | 목적지 없음, 홈 링크 |
| 404 | `NOT_FOUND` | 없는 경로·주차장 | 오류 |
| 500 | `INTERNAL_ERROR` | 서버 오류 | 오류와 재시도 |
| 네트워크 실패 | - | 서버 꺼짐·CORS 차단 | 오류와 재시도 |

429·503은 현재 백엔드가 보내지 않습니다.

---

## 4. 주차장 조회 API (보조)

목적지 흐름과 별개로 공공데이터 주차장(충남 1,143곳)을 직접 조회합니다. **이 그룹은 `id`가 숫자이고 좌표가 `latitude`·`longitude` 필드로 펼쳐져 있어 목적지 API와 형식이 다릅니다.** 직접 등록한 건물 주차장은 포함되지 않습니다.

### 4-1. 주차장 검색 `GET /api/parking-lots`
| 파라미터 | 설명 |
| --- | --- |
| `keyword` | 주차장명·주소 부분 일치 |
| `region` | 시·군 코드 (`CHEONAN` 등, 4-5 참고) |
| `category` | `PUBLIC`(공영) / `PRIVATE`(민영) |
| `feeType` | `FREE` / `PAID` / `MIXED` |
| `minSpaces` | 최소 주차면수 |
| `openNow` | `true`면 현재 운영 중만 |
| `page`, `size`, `sort` | 페이지 0부터, 기본 20개. `sort=name,asc` / `totalSpaces,desc` / `region,asc`. 없는 기준은 400 `INVALID_SORT` |

```json
{
  "content": [
    {
      "id": 137, "name": "5단지시장주차장", "region": "천안시", "category": "공영", "feeType": "유료",
      "address": "충청남도 천안시 서북구 양지21길 31", "totalSpaces": 29,
      "latitude": 36.8126333, "longitude": 127.1392105, "openNow": true
    }
  ],
  "page": 0, "size": 1, "totalElements": 102, "totalPages": 102, "hasNext": true
}
```
`openNow`는 운영시간 정보가 없으면 응답에서 빠집니다.

### 4-2. 주변 주차장 `GET /api/parking-lots/nearby`
| 파라미터 | 필수 | 설명 |
| --- | --- | --- |
| `lat` | ✅ | 35.9–37.1 (충남 범위) |
| `lng` | ✅ | 125.9–127.8 |
| `radius` | | 100–10000m, 기본 1000 |
| `limit` | | 1–200, 기본 50 |
| `feeType` | | `FREE` / `PAID` / `MIXED` |

```json
[
  {
    "id": 138, "name": "천안시청주차장", "region": "천안시", "category": "공영", "feeType": "무료",
    "address": "충청남도 천안시 서북구 번영로 156", "totalSpaces": 348,
    "latitude": 36.81612841, "longitude": 127.1130483, "openNow": true, "distanceMeters": 137
  }
]
```
`distanceMeters`는 직선거리입니다. 범위 밖 좌표는 400입니다.

### 4-3. 주차장 상세 `GET /api/parking-lots/{id}`
```json
{
  "id": 138, "managementNo": "302-3-000075", "name": "천안시청주차장", "region": "천안시",
  "category": "공영", "lotType": "부설",
  "roadAddress": "충청남도 천안시 서북구 번영로 156", "jibunAddress": "충청남도 천안시 서북구 불당동 234-1",
  "totalSpaces": 348, "operatingDays": "평일+토요일+공휴일",
  "hours": { "weekday": "00:00~23:59", "saturday": "00:00~23:59", "holiday": "00:00~23:59" },
  "openNow": true, "feeType": "무료",
  "fee": { "basicTime": 0, "basicCharge": null, "additionalUnitTime": null, "additionalUnitCharge": null,
           "dayPassCharge": null, "monthPassCharge": null },
  "paymentMethods": null, "notes": null, "managingAgency": "충청남도 천안시청", "phoneNumber": "041-521-5305",
  "latitude": 36.81612841, "longitude": 127.1130483, "referenceDate": "2026-04-16"
}
```
없는 id는 404 `NOT_FOUND`. 요금 단위는 분·원입니다.

### 4-4. 예상 요금 `GET /api/parking-lots/{id}/fee?minutes=90`
`minutes`: 1–44640(31일)
```json
{ "parkingLotId": 138, "name": "천안시청주차장", "minutes": 90, "feeType": "무료", "estimatedFee": 0, "message": "무료 주차장입니다." }
```
요금 정보가 없으면 `estimatedFee`는 `null`입니다. 1일권이 더 싸면 1일권 요금을 반환합니다.

### 4-5. 시·군 목록 `GET /api/regions`
```json
[ { "code": "CHEONAN", "name": "천안시", "parkingLotCount": 102 }, { "code": "GONGJU", "name": "공주시", "parkingLotCount": 184 } ]
```
코드: `CHEONAN` `GONGJU` `BORYEONG` `ASAN` `SEOSAN` `NONSAN` `GYERYONG` `DANGJIN` `GEUMSAN` `BUYEO` `SEOCHEON` `CHEONGYANG` `HONGSEONG` `YESAN` `TAEAN`

---

## 5. 관리자 API

### 공공데이터 동기화 `POST /api/admin/sync`
헤더 `X-Admin-Key` 필요. 공공데이터포털 전국주차장정보표준데이터에서 충남만 저장합니다 (관리번호 기준 갱신·추가). 운영 프로필에서는 매일 04:00 자동 실행됩니다.
```json
{ "fetched": 18994, "chungnam": 1163, "inserted": 0, "updated": 1153, "elapsedMs": 3480 }
```
| HTTP | code | 언제 |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | 키 없음·불일치 |
| 409 | `SYNC_UNAVAILABLE` | 인증키 미설정 또는 동기화 진행 중 |

---

## 6. 프론트 초안에 대한 백엔드 결정 사항

| 초안 항목 | 결정 |
| --- | --- |
| 경로 | 초안 그대로 `/places`, `/places/{placeId}/parking` (`/api` 접두사 없음) |
| 빈 검색 | 400이 아니라 **200 + 빈 목록** (목업 어댑터와 동일). 100자 초과만 400 |
| 페이지네이션 | 없음. 전체 결과 반환 |
| 오류 body | `{ code, message }` + `status`, `timestamp`. `message`는 표시용이 아님 |
| `entranceCoordinates` 필수 | 좌표 없는 주차장은 응답에서 제외. 공공데이터 주차장은 대표 좌표 사용 |
| 도보 시간 | 직접 등록한 값만 사용, 없으면 `null` / `[]` |
| 운영시간 경고 | 목적지에 등록된 문구를 그대로 반환 |
| 대체 주차장 추천 | 등록된 주변 주차장 + (선택) 반경 1km 공공데이터 최대 10곳, 도보 시간 오름차순·미확인 마지막 |

---

## 7. 미구현 (후속)

| 항목 | 내용 |
| --- | --- |
| 정확도 피드백 | `POST /parking-lots/{parkingLotId}/feedback` — 정확함/부정확함, 방문 목적지 ID, 중복·스팸 제한, 인증 정책 합의 후 구현 |
| 외부 장소 검색 | 카카오 로컬 API 등으로 등록되지 않은 장소 검색 (ID 매핑 규칙 합의 필요) |
| 목적지·주차장 등록 API | 지금은 서버 코드(시연 데이터)로만 등록 가능. 관리자용 등록·수정 API 필요 |
| 실제 보행 경로 | 도보 시간·동선 자동 계산 |
| 방문 예정 시각 기반 경고 | 시간대·자정·휴무 처리 합의 필요 |
| 입구 사진 | 사진 저장소·공개 URL 정책 |

---

변경 이력
- v1.0 (2026-11-16): 구현 기준 최종 명세. 실제 서버 응답 예시, 오류 코드, 결정 사항, 미구현 항목 정리
- v0.3: 목적지 API를 프론트 경로(`/places`) 그대로 구현
- v0.2: 프론트 초안 v0.1 기준 제안 명세
