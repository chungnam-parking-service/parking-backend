# 충남 주차 안내 백엔드 API 명세서 v0.3

> 프론트 초안 [`frontend/docs/API.md` v0.1]과 `src/domain/parking.ts` 타입을 기준으로 작성한 **백엔드 명세**입니다.
> 목적지 API(`/places`)는 프론트 API.md의 경로·응답 형식을 **그대로** 따릅니다.
> 시연 목적지 3곳의 응답은 프론트 `src/mocks/data.ts`와 완전히 같은지 자동 테스트로 확인합니다.
> 각 API 옆의 상태 표시로 구현 여부를 구분합니다. 확정 전까지는 협의를 거쳐 변경될 수 있습니다.
>
> | 표시 | 의미 |
> | --- | --- |
> | ✅ 구현됨 | 지금 서버에서 호출 가능 |
> | 🛠 개발 예정 | 응답 형식 확정, 백엔드 구현 중 |
> | 📝 협의 필요 | 형식·정책을 프론트와 더 정해야 함 |

---

## 0. 공통 규칙

### 기본 주소
| 환경 | Base URL |
| --- | --- |
| 로컬 | `http://localhost:8080` |
| 배포 | 미정 |

프론트 `.env`에 `VITE_API_BASE_URL=http://localhost:8080` 을 넣으면 `parkingApi.ts`의 HTTP 어댑터가 그대로 동작합니다.

| 그룹 | 경로 | 비고 |
| --- | --- | --- |
| 목적지 (프론트 API.md 계약) | `/places`, `/places/{placeId}/parking` | 프론트 경로 그대로, `/api` 없음 |
| 주차장 직접 조회·관리자 | `/api/parking-lots/...`, `/api/regions`, `/api/admin/...` | 기존 백엔드 API |

### 형식
- 요청·응답은 `application/json; charset=UTF-8`.
- **ID는 모두 문자열**입니다. (`"place-demo-cafe"`, `"kakao-12345"`, `"lot-138"`)
- 좌표는 WGS84 십진수, `{ "latitude": 36.815, "longitude": 127.1085 }` 형태로만 보냅니다.
- 날짜시각은 ISO 8601 + 서울 시간대 (`2026-11-07T09:00:00+09:00`).
- **모르는 값은 `null`, 목록은 빈 배열 `[]`** 로 보냅니다. 0분·0원·"무료"로 바꿔 채우지 않습니다.
- 초기에는 인증 없이 조회합니다. 관리자 API만 `X-Admin-Key` 헤더가 필요합니다.

### CORS
`http://localhost:5173`(Vite), `http://localhost:3000` 허용 중. 배포 주소가 정해지면 추가합니다.

### 오류 응답
모든 오류는 같은 형식입니다. 프론트 초안의 `{code, message}`에 `status`, `timestamp`가 더 붙어 있습니다.

```json
{
  "status": 404,
  "code": "PLACE_NOT_FOUND",
  "message": "목적지를 찾을 수 없습니다.",
  "timestamp": "2026-11-16T22:10:00.123"
}
```

| HTTP | code | 언제 | 프론트 화면 동작 (초안 기준) |
| --- | --- | --- | --- |
| 200 + 빈 `items` | - | 검색 결과 없음 | 빈 결과 안내 |
| 400 | `BAD_REQUEST`, `INVALID_PARAMETER`, `INVALID_SORT` | 입력값 오류 (빈 검색어, 100자 초과, 충남 밖 좌표 등) | 오류와 재시도 |
| 401 | `UNAUTHORIZED` | 관리자 키 없음/틀림 | (관리자 전용) |
| 404 | `PLACE_NOT_FOUND`, `NOT_FOUND` | 없는 목적지/주차장/주소 | 목적지 없음, 홈 링크 |
| 500 | `INTERNAL_ERROR` | 서버 오류 | 오류와 재시도 |
| 502 / 503 | `UPSTREAM_ERROR` | 외부 API(카카오 등) 실패 | 오류와 재시도 |

---

## 1. 목적지 검색 ✅ 구현됨

`GET /places?query={검색어}`

### 요청
| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `query` | string | ✅ | 앞뒤 공백 제거 후 1–100자. **공백뿐이면 200 + 빈 목록** (목업과 동일), 100자 초과는 400 |

### 동작
1. 백엔드 DB에 등록된 목적지를 **이름 + 주소 + 카테고리**로 검색합니다. 목업과 같이 공백을 무시하고 대소문자를 구분하지 않습니다 (`모퉁이커피` → "모퉁이 커피").
2. 페이지네이션 없이 전체 결과를 이름순으로 반환하고, `total`은 `items` 개수입니다.
3. (2단계 예정) 카카오 로컬 API 검색 결과 중 **충남 주소만** 뒤에 붙입니다.

### 응답 `200` — `SearchPlacesResponse`
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

### `Place` 필드
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | string | 등록 목적지는 `place-…`, 카카오 결과는 `kakao-{카카오장소ID}` |
| `name` | string | 목적지 이름 |
| `category` | string | 카페, 음식점 등. 모르면 `""` 📝 (타입이 non-null이라 빈 문자열) |
| `address` | string | 도로명 우선, 없으면 지번 |
| `coordinates` | Coordinates | 목적지 좌표 |
| `parkingStatus` | `"available"` \| `"unavailable"` \| `"unknown"` | 아래 기준 |
| `parkingSummary` | string | 한 줄 요약. 모르면 `"주차 정보 확인 중"` 📝 (타입이 non-null) |

### `parkingStatus` 결정 기준
| 값 | 백엔드 기준 |
| --- | --- |
| `available` | 이 목적지에 연결된 전용·공용·제휴 주차장이 1개 이상 등록됨. **실시간 빈자리 아님** |
| `unavailable` | 팀이 "목적지 자체 주차 불가"로 확인해 등록함 |
| `unknown` | 위 둘 다 아님 (카카오 검색으로만 들어온 목적지 대부분). **불가로 바꾸지 않음** |

---

## 2. 목적지 주차 상세 ✅ 구현됨

`GET /places/{placeId}/parking`

### 요청
| 파라미터 | 위치 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- | --- |
| `placeId` | path | string | ✅ | 검색 응답의 `id` |

### alternatives 구성
1. 팀이 직접 등록한 **주변 주차장**(`relationship: nearby`, 도보 시간·안내 포함)
2. 목적지 설정에 따라 **반경 1km 공공데이터 주차장 최대 10곳** 자동 추가 (도보 정보는 `null`/`[]`)
   - 시연 목적지 3곳은 목업과 같게 하려고 자동 추가를 꺼 두었습니다.

### 응답 `200` — `PlaceParkingResponse`
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
        "id": "lot-1201",
        "name": "모퉁이 빌딩 주차장",
        "address": "충남 천안시 불당동 · 예시 주소",
        "entranceCoordinates": { "latitude": 36.8148, "longitude": 127.1082 },
        "entrancePhotoUrl": null,
        "entranceGuide": "매장 정문 반대편, 건물 오른쪽 골목의 지하 입구로 들어가세요.",
        "feeDescription": "무료 시간 초과 시 10분당 500원",
        "hoursDescription": "매일 09:00–21:00",
        "verifiedAt": "2026-11-07T09:00:00+09:00"
      },
      "relationship": "shared",
      "benefitDescription": "음료 1잔 이상 구매 시 2시간 무료",
      "validationInstructions": "주문할 때 카운터에 차량번호 뒤 4자리를 알려주세요.",
      "walkingMinutes": 2,
      "walkingSteps": [
        "B1 주차 후 중앙 엘리베이터로 이동하세요.",
        "1층에서 내려 건물 정문으로 나오세요."
      ]
    }
  ],
  "alternatives": [
    {
      "lot": {
        "id": "lot-91",
        "name": "불당 제5공영주차장",
        "address": "충청남도 천안시 서북구 불당25로 154",
        "entranceCoordinates": { "latitude": 36.81491719, "longitude": 127.1079523 },
        "entrancePhotoUrl": null,
        "entranceGuide": null,
        "feeDescription": "기본 30분 500원 · 추가 10분당 200원 · 1일 5,000원",
        "hoursDescription": "평일 00:00–23:59 · 토 00:00–23:59 · 공휴일 00:00–23:59",
        "verifiedAt": null
      },
      "relationship": "nearby",
      "benefitDescription": null,
      "validationInstructions": null,
      "walkingMinutes": null,
      "walkingSteps": []
    }
  ],
  "isDemo": false
}
```

### 최상위 필드
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `place` | Place | 검색 응답과 같은 구조 |
| `placeHoursDescription` | string \| null | 목적지 운영시간 문구 |
| `operatingHoursWarning` | string \| null | 연결 주차장이 목적지보다 먼저 닫을 때 경고. 1단계는 등록된 문구, 4단계에서 자동 계산 📝 |
| `primaryParking` | PlaceParkingOption[] | 전용·공용·제휴 주차장. **없으면 `[]`** |
| `alternatives` | PlaceParkingOption[] | 주변 공공데이터 주차장. **없으면 `[]`** |
| `isDemo` | boolean | 시연용 예시 데이터면 `true` |

### `PlaceParkingOption`
| 필드 | 타입 | primary | alternatives (공공데이터) |
| --- | --- | --- | --- |
| `lot` | ParkingLot | 등록된 주차장 | 공공데이터 주차장 |
| `relationship` | `dedicated` \| `shared` \| `partner` \| `nearby` | 전용/건물 공용/제휴 | 항상 `nearby` |
| `benefitDescription` | string \| null | 매장 할인 조건 | `null` |
| `validationInstructions` | string \| null | 주차 등록·정산 방법 | `null` |
| `walkingMinutes` | number \| null | 등록된 도보 분 | `null` (4단계에서 경로 API로 계산) |
| `walkingSteps` | string[] | 등록된 도보 안내 | `[]` |

### `ParkingLot`
| 필드 | 타입 | 등록 주차장 | 공공데이터 주차장 |
| --- | --- | --- | --- |
| `id` | string | `lot-{번호}` | `lot-{번호}` |
| `name` | string | 등록값 | 공공데이터 주차장명 |
| `address` | string | 등록값 | 도로명 우선, 없으면 지번 |
| `entranceCoordinates` | Coordinates | **등록한 입구 좌표** | 공공데이터 주차장 좌표 (입구가 아닐 수 있음) 📝 |
| `entrancePhotoUrl` | string \| null | 등록된 사진 URL | `null` |
| `entranceGuide` | string \| null | 등록된 입구 안내 | `null` |
| `feeDescription` | string \| null | 등록 문구 | 공공데이터 요금으로 생성 (무료면 `"무료"`, 정보 없으면 `null`) |
| `hoursDescription` | string \| null | 등록 문구 | 공공데이터 운영시간으로 생성, 없으면 `null` |
| `verifiedAt` | string \| null | 팀이 마지막으로 확인한 시각 | `null` |

### 정렬·필터 규칙
- `alternatives`는 **`walkingMinutes` 오름차순, `null`은 마지막**. `null`끼리는 서버가 직선거리 순으로 놓습니다. 단, 직선거리로 도보 시간을 만들어 보내지는 않습니다.
- `primaryParking`에 이미 있는 주차장은 `alternatives`에서 뺍니다.
- 좌표가 없는 공공데이터 주차장은 `entranceCoordinates`가 필수라 보내지 않습니다.

### 오류
| HTTP | code | 언제 |
| --- | --- | --- |
| 404 | `PLACE_NOT_FOUND` | 없는 `placeId` → `{"code":"PLACE_NOT_FOUND","message":"목적지를 찾을 수 없습니다."}` |

### 시연 데이터
서버를 켜면(운영 프로필 제외) 프론트 목업과 **같은 ID·같은 값**의 목적지 3곳이 자동 등록됩니다.

| placeId | parkingStatus | 확인 포인트 |
| --- | --- | --- |
| `place-demo-cafe` | available | 연결 주차장 1 + 주변 주차장 1, 운영시간 경고 |
| `place-demo-books` | unavailable | 연결 없음, 주변 주차장 1 |
| `place-demo-food` | unknown | 연결·주변 모두 `[]` → 출발 버튼 없음 |

---

## 3. 주차장 직접 조회 API ✅ 구현됨

목적지 흐름과 별개로 지금 바로 쓸 수 있는 API입니다. 지도 화면이나 "주변 주차장 전체 보기"에 활용할 수 있습니다.
이 API들의 `id`는 숫자이고 응답 형태가 목적지 API와 다릅니다. 상세 형식은 Swagger(`/swagger-ui.html`)를 기준으로 합니다.

| Method | URL | 설명 |
| --- | --- | --- |
| GET | `/api/parking-lots` | 조건 검색. `keyword`, `region`, `category`(PUBLIC/PRIVATE), `feeType`(FREE/PAID/MIXED), `minSpaces`, `openNow`, `page`(0부터), `size`, `sort` |
| GET | `/api/parking-lots/nearby` | 반경 검색. `lat`, `lng`(충남 범위), `radius`(100–10000m), `limit`, `feeType`. 응답에 `distanceMeters`(직선거리) 포함 |
| GET | `/api/parking-lots/{id}` | 주차장 상세 (운영시간·요금표·연락처) |
| GET | `/api/parking-lots/{id}/fee?minutes=90` | 예상 요금 계산 |
| GET | `/api/regions` | 충남 15개 시·군 코드·이름·주차장 수 |

페이지 응답 형식:
```json
{ "content": [ ... ], "page": 0, "size": 20, "totalElements": 1143, "totalPages": 58, "hasNext": true }
```

---

## 4. 관리자 API (백엔드·데이터 담당용)

프론트 화면에서는 호출하지 않습니다. 헤더 `X-Admin-Key` 필요.

| Method | URL | 상태 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/admin/sync` | ✅ | 공공데이터 주차장 동기화 (충남 1,143곳) |
| POST | `/api/admin/places` | 🛠 3단계 | 목적지 등록 |
| PUT | `/api/admin/places/{placeId}` | 🛠 3단계 | 목적지 수정 (주차 상태, 요약, 운영시간) |
| POST | `/api/admin/parking-lots` | 🛠 3단계 | 건물 주차장 등 공공데이터에 없는 주차장 등록 (입구 좌표·사진·안내) |
| POST | `/api/admin/places/{placeId}/parking` | 🛠 3단계 | 목적지 ↔ 주차장 연결 (관계, 혜택, 등록 방법, 도보 안내) |

---

## 5. 후속 MVP

| Method | URL | 상태 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/parking-lots/{parkingLotId}/feedback` | 📝 | 정확함/부정확함 피드백. 방문 목적지 ID, 중복·스팸 제한, 인증 정책 합의 후 구현 |

방문 예정 시각 기반 경고, 실제 입구 사진, 실제 보행 경로(TMAP 등)는 4단계에서 다룹니다.

---

## 6. 협의가 필요한 항목

| # | 항목 | 백엔드 제안 | 결정 |
| --- | --- | --- | --- |
| 1 | 목적지 검색 출처 | 등록 목적지 + 카카오 로컬 검색(충남만) 결합 | |
| 2 | 외부 장소 ID | `kakao-{카카오ID}` 형식, 검색 시 DB에 저장 | |
| 3 | 검색 개수·페이지네이션 | 1차는 최대 20개, 페이지네이션 없음 | |
| 4 | `category`, `parkingSummary`를 모를 때 | 타입이 non-null이라 `""` / `"주차 정보 확인 중"` (또는 타입을 nullable로 변경) | |
| 5 | 공공데이터 주차장의 입구 좌표 | 주차장 대표 좌표를 그대로 사용 (입구와 다를 수 있음을 화면에 표시?) | |
| 6 | 도보 시간 | 1차는 등록값만, 없으면 null. 4단계에서 경로 API | |
| 7 | 대체 주차장 반경·개수 | 반경 1km, 최대 10곳 | |
| 8 | 운영시간 경고 | 1차는 등록 문구, 이후 자동 계산 | |
| 9 | 오류 body | 현재 형식(`status, code, message, timestamp`) 유지 | |
| 10 | 매장별 주차 정보 수집 담당 | 시연 매장 3곳부터 팀이 조사 후 관리자 API로 입력 | |

---

## 7. 개발 일정 (제안)

| 단계 | 내용 | 프론트 영향 |
| --- | --- | --- |
| 1단계 ✅ | `/places`, `/places/{id}/parking` 응답 형식 구현. 시연 매장 3곳(`place-demo-*`) 동일 ID로 등록. 대체 주차장은 공공데이터 연결 | `VITE_API_BASE_URL=http://localhost:8080` 설정만으로 목업 → 실서버 전환 |
| 2단계 | 카카오 로컬 검색 연동 (충남 필터) | 실제 장소 검색 가능 |
| 3단계 | 관리자 API로 매장별 주차 정보 입력 | 주차 가능 목적지 증가 |
| 4단계 | 도보 경로 계산, 운영시간 경고 자동화, 정확도 피드백 | 도보 시간·경고 표시 |

---

변경 이력
- v0.3 (2026-11-16): 목적지 API를 프론트 API.md 경로(`/places`) 그대로 구현, 시연 데이터·alternatives 구성 규칙 추가
- v0.2 (2026-11-16): 프론트 초안 v0.1 기준 백엔드 명세 작성, 기존 주차장 API 정리, 협의 항목 추가
