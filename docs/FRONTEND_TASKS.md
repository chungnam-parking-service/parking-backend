# 프론트엔드 작업 요청 — 백엔드 v1.4 연동

작성일: 2026-11-19 · 백엔드 담당 · 기준: 백엔드 API 명세 [`docs/API_SPEC.md`](API_SPEC.md) v1.4

디자인(Figma)과 프론트 `INTEGRATION.md`의 "아직 서버에 없다" 목록을 바탕으로 백엔드에 기능을 추가했습니다. 프론트는 **지금 코드 그대로 동작**하고, 아래 화면 작업을 하면 새 기능을 쓸 수 있습니다.

## 한눈에 보기

| # | 프론트 작업 | 근거 | 쓰는 API | 우선순위 |
| --- | --- | --- | --- | --- |
| 1 | 도보 거리·시간 "약" 표시 | 디자인 도보 화면 | 상세 응답의 새 선택 필드 | 높음 (작음) |
| 2 | "정보가 맞아요 / 달라요" 버튼 | INTEGRATION "정확도 피드백" | `POST …/feedback` (새로 만듦) | 높음 (작음) |
| 3 | 주차 정보 등록·수정 + 사진 업로드 | 디자인 메뉴, 상세 사진 영역 | `POST /places/{id}/reports` (새로 만듦) | 높음 (큼) |
| 4 | "정보 확인됨" 배지 | 디자인 "정보 있음" | 기존 `verifiedAt` | 보통 |
| 5 | 주차 비서 채팅 | 백엔드 PR #2 | `POST /assistant/chat` (이미 있음) | 팀 결정 |
| 6 | 배포 주소 전달·연결 | INTEGRATION "배포" | — | 높음 |

답변이 필요한 것: **프론트 배포 플랫폼과 주소** (6번), **주차 비서 화면을 넣을지** (5번).

## 1. 도보 거리·시간 "약" 표시

디자인 도보 안내 화면의 "도보 거리 약 250m · 예상 소요 시간 약 3분"을 위해 `PlaceParkingOption`에 선택 필드 2개를 추가했습니다. `walkingMinutes`는 지금처럼 확인된 값만 담기 때문에 기존 화면·테스트는 그대로 동작합니다.

| 필드 | 타입 | 뜻 |
| --- | --- | --- |
| `distanceMeters` | number (선택) | 목적지 ↔ 주차장 직선거리(m) |
| `estimatedWalkingMinutes` | number (선택) | 직선거리 × 1.3 ÷ 75m/분으로 **추정한** 도보 분 (최소 1) |

값을 모르면 필드 자체가 없습니다 (`null`로 보내지 않음). 시연 목적지 응답은 바뀌지 않았습니다.

```json
{
  "lot": { "id": "osm-way-472630487", "name": "이름 없는 주차장" },
  "relationship": "nearby",
  "walkingMinutes": null,
  "walkingSteps": [],
  "distanceMeters": 48,
  "estimatedWalkingMinutes": 1
}
```

**표시 규칙 (제안)**

1. `walkingMinutes`가 있으면 "도보 N분" (확인된 값)
2. 없고 `estimatedWalkingMinutes`가 있으면 "도보 약 N분"
3. `distanceMeters`가 있으면 "약 250m" (1,000m 이상은 "약 1.2km")
4. 둘 다 없으면 지금처럼 "도보 정보 없음"

**해야 할 곳:** 목적지 화면의 주차장 카드("매장에서 도보 약 3분"), 도보 안내 화면(거리·시간 칸), 주변 주차장 정렬(현재 `walkingMinutes`만 보므로 추정값도 반영 권장).

실제 보행 경로가 아니라서 계단·횡단보도가 반영되지 않습니다. 반드시 "약"을 붙여 주세요. 경로선은 지금처럼 그리지 않습니다.

## 2. 정확도 피드백 버튼

주차장 상세 화면에 **"정보가 맞아요 / 달라요"** 버튼을 넣어 주세요. 모인 의견으로 관리자가 대표 위치를 실제 입구로 바꿉니다. 새 API는 이미 만들어 두었습니다.

`POST /places/{placeId}/parking/{lotId}/feedback` (`lotId` = 상세 응답의 `lot.id`)

```json
{ "accurate": false, "comment": "입구가 건물 뒤편이에요" }
```

응답 201:

```json
{ "id": 1, "placeId": "sangga-MA010120220803118414", "lotId": "osm-way-472630487", "accurate": false, "createdAt": "2026-11-19T16:00:03+09:00" }
```

| 상황 | 응답 | 화면 제안 |
| --- | --- | --- |
| 성공 | 201 | "알려 주셔서 고마워요" + 버튼 비활성 |
| 같은 사람이 24시간 안에 다시 보냄 | 409 `DUPLICATE_FEEDBACK` | "이미 의견을 보냈어요" (오류처럼 보이지 않게) |
| 없는 목적지·주차장 | 404 `PLACE_NOT_FOUND` / `LOT_NOT_FOUND` | 일반 오류 문구 |
| `accurate` 없음, 의견 300자 초과 | 400 | 입력 확인 문구 |

- `comment`는 선택입니다. "달라요"를 누른 뒤 한 줄 입력란을 보여주는 방식을 권합니다.
- `isDemo: true` 목적지에서는 버튼을 숨기거나 목 모드처럼 요청을 보내지 않는 쪽을 권합니다.
- 보낸 뒤 기기에 "보냈음"을 저장해 두면 새로고침 후에도 버튼을 비활성으로 유지할 수 있습니다 (서버는 409로 막음).

## 3. 주차 정보 등록·수정 화면 + 입구 사진

디자인 메뉴의 "주차 정보 등록"과 상세의 "주차장 이미지 영역"을 위한 API입니다. 지금 소개 화면의 "등록·수정 접수 기능을 준비하고 있어요" 문구를 실제 화면으로 바꿀 수 있습니다. 보낸 정보는 **관리자가 승인한 뒤에만** 화면에 나옵니다.

`POST /places/{placeId}/reports`

**입력 칸 (모두 선택, 하나 이상 필요)**

| 필드 | 화면 입력 | 제한 |
| --- | --- | --- |
| `lotId` | 어느 주차장을 고칠지 (상세에서 들어오면 자동) | 비우면 "목적지 전체 제보" (새 주차장 알림 등, 자동 반영 안 됨) |
| `entranceGuide` | 차량 진입 안내 | 500자 |
| `feeDescription` | 요금 | 300자 |
| `hoursDescription` | 운영시간 | 300자 |
| `benefitDescription` | 주차 혜택 | 300자 |
| `validationInstructions` | 등록·정산 방법 | 500자 |
| `walkingSteps` | 도보 안내 단계 | 최대 10개, 각 300자 |
| `entranceLatitude`, `entranceLongitude` | 지도에서 입구 위치 찍기 | 둘 다 또는 둘 다 생략 |
| `comment` | 관리자에게 한마디 | 1,000자, 화면에 안 나옴 |
| `photo` | 입구 사진 한 장 | JPG·PNG·WebP, 5MB |

**사진 없이:** `Content-Type: application/json`으로 위 필드를 JSON으로 보냅니다.

**사진과 함께:** `multipart/form-data`. `report` 파트는 JSON, `photo` 파트는 파일입니다.

```ts
const form = new FormData();
form.append('report', new Blob([JSON.stringify(report)], { type: 'application/json' }));
if (file) form.append('photo', file);
await fetch(`${base}/places/${encodeURIComponent(placeId)}/reports`, { method: 'POST', body: form });
// Content-Type 헤더는 직접 넣지 마세요 (브라우저가 boundary 포함해서 정함)
```

응답 201:

```json
{ "id": 1, "status": "pending", "placeId": "sangga-MA010120220803118414", "lotId": "osm-way-472630487", "photoUrl": "http://localhost:8080/uploads/907c9688-08ec-4231-b3f1-e8382b9b766e.png", "createdAt": "2026-11-19T16:00:43+09:00" }
```

| 상황 | 응답 | 화면 제안 |
| --- | --- | --- |
| 접수 | 201, `status: "pending"` | "보내 주셔서 고마워요. 확인 후 반영할게요" |
| 모두 빈칸 | 400 `EMPTY_REPORT` | "알려 주실 내용을 하나 이상 입력해 주세요" |
| 사진이 아닌 파일 | 400 `INVALID_PHOTO` | "JPG, PNG, WebP만 올릴 수 있어요" |
| 5MB 초과 | 413 `PHOTO_TOO_LARGE` | "5MB 이하로 올려 주세요" (올리기 전 프론트에서도 크기 확인 권장) |
| 하루 10건 초과 | 429 `TOO_MANY_REPORTS` | "오늘은 더 보낼 수 없어요" |
| 없는 목적지·주차장 | 404 | 일반 오류 문구 |

**승인되면 기존 상세 응답이 바뀝니다 (새 필드 없음).** `entrancePhotoUrl`에 사진 절대 URL, `entranceGuide`·요금·운영시간·혜택·등록 방법·도보 단계가 바뀌고, 입구 좌표를 보냈으면 `coordinateType`이 `"entrance"`가 됩니다. `verifiedAt`은 승인 시각입니다. 현재 화면이 이미 이 필드들을 표시하므로 추가 작업은 없습니다.

화면 제안: 주차장 상세에 "정보 고치기" 버튼(해당 `lotId` 자동 입력) + 메뉴 "주차 정보 등록"에서 목적지 검색 후 제보.

## 4. 확인된 정보 표시 (디자인의 "정보 있음" 배지)

새 필드는 만들지 않았습니다. 이미 있는 `verifiedAt`으로 구분할 수 있습니다.

| 조건 | 뜻 | 배지 제안 |
| --- | --- | --- |
| `verifiedAt`이 있음 | 팀이 확인했거나 사용자 제보를 관리자가 승인함 | "정보 확인됨" + 날짜 |
| `verifiedAt`이 `null`, 요금·운영시간 문구가 있음 | 공공데이터·AI 데이터 그대로 | "공공 정보" |
| 둘 다 없음 | 정보 부족 | "정보 등록하기" → 3번 제보 화면으로 |

- 입구 정확도는 지금처럼 `coordinateType`(입구 확인 / 대표 위치 / 입구 미확인)을 쓰면 됩니다.
- 제보가 승인되면 `verifiedAt`이 생기고, 입구 좌표를 보낸 경우 `coordinateType`이 `entrance`로 바뀌어 배지가 자연스럽게 올라갑니다.
- 배지 문구는 디자인 담당과 정해 주세요.

## 5. 주차 비서 채팅 화면

백엔드에는 이미 `POST /assistant/chat`이 있습니다 (팀원 PR #2). 프론트에 채팅 화면만 있으면 됩니다. 디자인에는 없는 화면이라 **넣을지부터 팀이 정해 주세요.**

요청:

```json
{
  "message": "천안 직산 배나무숲 주차 돼요?",
  "history": [
    { "role": "user", "content": "이전 질문" },
    { "role": "assistant", "content": "이전 답변" }
  ]
}
```

응답 200:

```json
{ "answer": "배나무숲은 매장 주차장이 있고 무료예요. …", "toolCalls": [], "model": "…" }
```

| 규칙 | 내용 |
| --- | --- |
| `message` | 1–500자. 빈 값이면 400 |
| `history` | 최대 10개, 각 4,000자 이하. `role`은 `user` 또는 `assistant` |
| 응답 시간 | **4–16초**. 현재 프론트 10초 제한보다 길어서, 이 요청만 제한 시간을 30–60초로 늘려야 합니다 |
| 503 `ASSISTANT_UNAVAILABLE` | 백엔드에 AI 서버가 연결되지 않음 → "주차 비서를 지금 쓸 수 없어요" |
| 503 `ASSISTANT_FAILED` | AI 응답 실패 → "잠시 후 다시 시도해 주세요" + 다시 시도 버튼 |

- `answer`에는 마크다운이 일부 섞일 수 있습니다. HTML로 그대로 넣지 말고 일반 텍스트(또는 안전한 마크다운 렌더러)로 표시해 주세요.
- `toolCalls`는 디버그용이라 화면에 보여주지 않아도 됩니다.
- 대화 기록은 프론트가 들고 있다가 `history`로 보냅니다. 서버는 저장하지 않습니다.

## 6. 배포 연결

백엔드는 배포 설정을 준비해 두었습니다 (Dockerfile, 포트·CORS 환경변수, 첫 실행 때 AI 데이터 자동 가져오기). 프론트에서 필요한 것은 아래 세 가지입니다.

1. **프론트 배포 주소를 알려주세요.** 레포에 `vercel.json`과 Netlify용 `_redirects`가 둘 다 있어서 어느 쪽인지 정해 주세요. 백엔드는 그 주소를 `CORS_ALLOWED_ORIGINS`에 넣습니다. 미리보기 배포 주소는 매번 바뀌므로 **본 주소 하나**로 정해 주세요.
2. 백엔드 배포 후 전달하는 HTTPS 주소를 프론트 배포 설정의 `VITE_API_BASE_URL`에 넣어 주세요.
3. 사진 URL(`entrancePhotoUrl`)은 백엔드 주소(`https://백엔드/uploads/…`)로 옵니다. `<img>`로 바로 쓰면 되고 CORS 설정은 필요 없습니다.

프론트가 보내는 요청은 모두 사용자 인증 없이 동작합니다. 관리자 API(`/api/admin/**`)와 관리자 키는 프론트에 연결하지 마세요.

## 7. parking.ts 스키마에 추가할 것

기존 스키마는 그대로 통과합니다 (백엔드 새 코드로 프론트 테스트 36개 통과 확인). 새 기능을 쓸 때 아래를 더해 주세요.

```ts
// parkingOptionSchema 에 추가 (선택 필드)
distanceMeters: z.number().int().nonnegative().optional(),
estimatedWalkingMinutes: z.number().int().positive().optional(),

// 정확도 피드백
export const feedbackResponseSchema = z.object({
  id: z.number(),
  placeId: z.string(),
  lotId: z.string(),
  accurate: z.boolean(),
  createdAt: z.string().datetime({ offset: true }),
});

// 주차 정보 제보 접수
export const reportReceiptSchema = z.object({
  id: z.number(),
  status: z.enum(['pending', 'approved', 'rejected']),
  placeId: z.string(),
  lotId: z.string().nullable(),
  photoUrl: z.string().url().nullable(),
  createdAt: z.string().datetime({ offset: true }),
});

// ParkingApi 인터페이스에 추가
sendFeedback(placeId: string, lotId: string, body: { accurate: boolean; comment?: string }): Promise<FeedbackResponse>;
sendReport(placeId: string, report: ReportInput, photo?: File): Promise<ReportReceipt>;
```

- 오류는 지금처럼 HTTP 상태 기준으로 문구를 고르되, 새 코드(`DUPLICATE_FEEDBACK`, `EMPTY_REPORT`, `INVALID_PHOTO`, `PHOTO_TOO_LARGE`, `TOO_MANY_REPORTS`)는 본문의 `code`로 구분해 주세요.
- 목 어댑터(`mockParkingApi`)에도 두 메서드를 넣어 목 모드에서도 화면을 확인할 수 있게 하면 좋습니다.
- 요청 제한 시간: 사진 업로드는 10초보다 길게(예: 30초) 잡는 것을 권합니다.

## 확인 방법과 주의

백엔드 변경은 [parking-backend PR #3](https://github.com/chungnam-parking-service/parking-backend/pull/3)에 있습니다. **PR이 `main`에 병합된 뒤** 이 문서의 API를 쓸 수 있습니다. 명세는 백엔드 레포 `docs/API_SPEC.md` v1.4 (2-3장 피드백, 2-4장 제보, 5-1장 배포)에 있습니다.

백엔드에서 확인한 것 (2026-11-19):

- 백엔드 테스트 61개 통과 (피드백 6개, 제보 8개, 도보 거리 1개 새로 추가). 시연 목적지 응답은 프론트 목업과 그대로 같음.
- 로컬 MySQL에서 피드백 → 사진 제보 → 관리자 승인 → 상세 응답 반영까지 실제로 확인. 반영된 응답도 프론트 `placeParkingSchema` 통과.
- 프론트 최신 `main`의 테스트 36개(AI 실연동 포함)가 새 백엔드에서 모두 통과.

로컬에서 붙여 보는 법 (백엔드 반영 후):

1. 백엔드를 `AI_CARDS_IMPORT_ON_STARTUP=true`, `ADMIN_API_KEY=아무값`으로 실행 (H2여도 AI 매장 데이터가 자동으로 들어옴, 약 1분)
2. 프론트를 `VITE_API_BASE_URL=http://localhost:8080`으로 실행
3. 화면에서 제보를 보낸 뒤 `http://localhost:8080/swagger-ui.html` → Authorize에 관리자 키 → `POST /api/admin/reports/{id}/approve`로 승인하고 상세 화면을 다시 열어 확인

주의:

- 로컬 서버의 사진 URL은 `http://localhost:8080/uploads/…`입니다. 휴대폰으로 테스트할 때는 보이지 않을 수 있어요.
- "같은 사람" 판단은 IP 기준이라, 같은 와이파이를 쓰는 팀원끼리는 같은 사람으로 보일 수 있습니다 (피드백 409가 나오면 이 때문일 수 있음).
