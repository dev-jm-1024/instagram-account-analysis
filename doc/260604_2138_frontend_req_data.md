# 프론트엔드 ↔ 백엔드 요청 데이터 분석

> 작성: 2026-06-04 21:38
> 대상: `frontend/src/domain/**` (프"론트 도메인 모델) ↔ `src/main/java/com/instagram/analyze/**` (Spring Boot REST API)
> 목적: 프론트엔드가 백엔드에 **요청해야 할 데이터**를 화면·도메인 타입 단위로 정리하고, 현재 프론트 모델과 백엔드 응답 사이의 **불일치(갭)** 를 식별한다.

---

## 0. 전제 — 아키텍처 전환

현재 프론트엔드(`frontend/`)는 **100% 클라이언트 사이드**로 동작한다.

- `services/parsing/*` 가 업로드된 raw JSON을 브라우저에서 파싱
- 결과를 거대한 단일 상태 `InstagramDataState` 로 메모리 보관 + `localStorage` 영속
- `services/demo/demoData.ts` 로 데모 데이터 제공

반면 백엔드(`src/main/java`, Spring Boot 4 / Java 21)는 **서버 사이드 ETL + 집계 조회 API** 구조다.

- `POST /api/import` 에 **압축 해제된 export 폴더 경로**를 넘기면 서버가 파싱(ETL)
- 이후 메뉴별 **집계된 읽기 전용 엔드포인트**(`/api/overview`, `/api/follows` …)로 조회

따라서 백엔드 연동 시 프론트의 파싱/데모/localStorage 계층은 **API 호출 계층으로 대체**되어야 하며, 일부 화면은 백엔드가 제공하는 데이터 형태에 맞춰 **재설계**가 필요하다(§4, §5 참조).

---

## 1. 공통 통신 규약

| 항목 | 값 |
|---|---|
| Base URL | `http://localhost:8374` (포터블 로컬 앱 고정 포트) |
| 프론트 dev origin | `http://localhost:5173` (Vite) |
| **CORS** | 기본 **비활성**. dev 연동 시 `application.yaml` 의 `instagram.web.cors.allowed-origins` 에 `http://localhost:5173` 추가 필요. 운영은 동일 origin 번들 가정 |
| 직렬화 | Jackson `default-property-inclusion: non_null` → **null 필드 키는 응답에서 제거됨** (프론트 계약: 키 부재 == null) |
| 허용 메서드 | `GET`, `POST` |

### 1.1 조회 응답 envelope — `ApiResponse<T>`

모든 **조회(GET)** 엔드포인트는 동일한 봉투로 감싼다.

```jsonc
{
  "code": null,        // 정상: null
  "message": null,     // 정상: null
  "data": { /* T */ }
}
```

- **정상**: `code=null, message=null, data=...`
- **데이터 없음(G4, HTTP 200)**: `code="SEARCH_HISTORY_NOT_FOUND"` 등 + `data=빈 결과`. **에러가 아님** — 프론트는 `code` 가 있으면 "데이터 없음" UI로 분기.
- ⚠️ **예외**: 임포트 엔드포인트(`/api/import*`)는 envelope 없이 `ImportStatusResponse` 를 **직접** 반환한다.

### 1.2 에러 응답 — `ErrorResponse` (4xx/5xx)

```jsonc
{
  "code": "DM_OWNER_NOT_RESOLVED", // 프론트 분기 기준
  "message": "본인 식별이 필요합니다. 사용자 이름을 먼저 입력해주세요.",
  "detail": "...",                  // 디버그용, nullable
  "httpStatus": 409
}
```

프론트는 **HTTP status 가 아니라 `code` 로 분기**하는 것을 권장한다(§6 에러 처리 표).

---

## 2. 도메인 타입 ↔ 엔드포인트 매핑 (요약)

| 프론트 도메인 타입 (`src/domain/types.ts`) | 대응 엔드포인트 | 매핑 상태 |
|---|---|---|
| `InstagramDataState.followers/following/closeFriends/recentlyUnfollowed/pendingFollowRequests/restrictedProfiles` (`FollowNode[]`) | `GET /api/follows?type=...` | 🟡 부분 — `href` 미제공(파생 가능) |
| `InstagramDataState.chatRooms` (`ChatRoom[]`, `MessageNode[]`) | `GET /api/messages/stats` | 🔴 **갭 — 메시지 본문/대화 로그 없음**, 통계만 |
| `InstagramDataState.likes/comments/posts` (`LikeNode[]/CommentNode[]/PostNode[]`) | `GET /api/activity?type=...` | 🔴 **갭 — 개별 항목 없음**, 월별 집계만 |
| `InstagramDataState.searches` (`SearchNode[]`) | `GET /api/searches` | 🟡 부분 — 빈도만, 개별 timestamp 타임라인 없음 |
| `InstagramDataState.logins` (`LoginNode[]`) | `GET /api/logins` | 🟡 부분 — `location`/`device_ua` 없음, `LOGOUT` 포함 |
| `InstagramDataState.miscLogs` (`MiscLogNode[]`) | `GET /api/logs` | 🟡 형태 상이 — 파일별 row 맵 배열 |
| (Overview 화면 집계) | `GET /api/overview` | 🟢 양호 |
| (히트맵 화면) | `GET /api/heatmap` | 🟡 부분 — **요일 인덱스 규약 상이** |
| (탐색기 화면) | `GET /api/explorer/tree`, `/file` | 🟢 양호(목적은 다름) |
| `InstagramDataState.isDemo` | — | ⚪ 백엔드 무관 (프론트 전용 데모 개념) |

🟢 양호 · 🟡 부분/주의 · 🔴 갭 · ⚪ 해당 없음

---

## 3. 임포트 흐름 (Uploader 화면의 전면 재설계 필요)

현재 프론트 `Uploader` 는 **JSON 파일 드래그&드롭**. 백엔드는 **서버 머신의 폴더 경로**를 받아 ETL 한다(파일 업로드가 아님). → 경로 입력 + 진행률 폴링 + 본인 식별 fallback UX로 변경 필요.

### 3.1 `POST /api/import` — ETL 시작 (논블로킹)

요청:
```json
{ "folderPath": "/Users/me/Downloads/instagram-export" }
```
응답: `ImportStatusResponse` (직접 반환, envelope 없음)
```jsonc
{
  "status": "IN_PROGRESS",      // IDLE | IN_PROGRESS | COMPLETED | FAILED
  "ownerResolved": false,
  "durationMillis": 0,
  "parsedItemCount": 0,
  "warnings": [ { "code": "SCHEMA_MISMATCH", "source": "...", "detail": "..." } ]
}
```
- 빈 경로 → `400 IMPORT_PATH_BLANK`
- 경로 없음/폴더 아님 → `400 IMPORT_PATH_NOT_FOUND` / `IMPORT_PATH_NOT_DIRECTORY`
- export 폴더 아님 → `422 IMPORT_NOT_INSTAGRAM_EXPORT`, HTML 포맷 → `422 IMPORT_HTML_ONLY`

### 3.2 `GET /api/import/status` — 진행률 폴링

응답: `ImportStatusResponse` 동일 형태. 프론트는 `status` 가 `COMPLETED`/`FAILED` 가 될 때까지 폴링. `parsedItemCount`, `durationMillis` 로 진행 표시, `warnings[]` 로 스킵된 항목 안내.

### 3.3 `POST /api/import/owner` — 본인 식별 수동 입력 (fallback)

자동 식별 실패 시 DM 통계 등에서 `409 DM_OWNER_NOT_RESOLVED` 가 나면 호출.
```json
{ "username": "my_instagram_id" }
```
- 빈 값 → `400 OWNER_INPUT_BLANK`
- 응답: `ImportStatusResponse` (`ownerResolved: true` 기대)

> `ParseWarning.code` 종류: `SCHEMA_MISMATCH`, `TIMESTAMP_INVALID`, `VALUE_BLANK`, `STRING_LIST_EMPTY`, `JSON_ERROR`, `FILE_EMPTY`, `STRING_DECODE_FAILED`.

---

## 4. 화면별 요청 명세

### 4.1 요약 대시보드 — `GET /api/overview`

응답 `ApiResponse<OverviewResponse>`:
```jsonc
{
  "data": {
    "importRequired": false,        // 임포트 미완료면 true + data:null
    "data": {
      "followerCount": 0, "followingCount": 0, "mutualCount": 0, "iFollowOnlyCount": 0,
      "totalPostCount": 0, "conversationCount": 0,
      "messageCount": 0, "likeCount": 0, "commentCount": 0,
      "activityFrom": 1700000000000, "activityTo": 1730000000000, // epoch ms, nullable
      "mostActiveMonth": "2026-03"
    }
  }
}
```
**프론트 매핑** (`useOverviewStats` 대체):
- 카드 4종 = `followerCount`, `mutualCount`, `conversationCount`/`messageCount`, `totalPostCount`, `likeCount`+`commentCount`
- 계정 활동 연령 = `activityFrom`~`activityTo` 로 계산(프론트 `accountLifespan` 로직 재사용 가능)
- ⚠️ **Golden Hour(피크 *시각*)** 는 overview에 없음 → `mostActiveMonth`(피크 *월*) 로 대체하거나 `/api/heatmap` 의 `peak` 사용
- ⚠️ "소울메이트(top chat partner)" 는 overview에 없음 → `/api/messages/stats` 의 `topPartners[0]` 로 별도 조회
- `importRequired:true` 면 업로더로 유도 (프론트 `isDemo`/데모 개념과는 별개)

### 4.2 팔로우 보기 — `GET /api/follows?type=...`

`type` ∈ `ALL`(기본) `| MUTUAL | I_FOLLOW_ONLY | FOLLOWS_ME_ONLY | UNFOLLOWED | PENDING | CLOSE_FRIENDS | RESTRICTED`

응답 `ApiResponse<FollowResponse>`:
```jsonc
{
  "data": {
    "followerCount": 0, "followingCount": 0, "mutualCount": 0,
    "iFollowOnlyCount": 0, "followsMeOnlyCount": 0,
    "items": [ { "username": "user_x", "followedAt": 1729452000000 } ] // followedAt nullable
  }
}
```
**프론트 매핑** (`Relations` + `computeRelationBuckets` 대체):
- 프론트의 7개 모드 ↔ `FollowQueryType`:
  - `dontFollowBack`(내가 짝사랑) → `I_FOLLOW_ONLY`
  - `dontFollowMe`(나를 짝사랑) → `FOLLOWS_ME_ONLY`
  - `mutual` → `MUTUAL`, `closeFriends` → `CLOSE_FRIENDS`, `unfollowed` → `UNFOLLOWED`, `pending` → `PENDING`, `restricted` → `RESTRICTED`
- 카운트 배지는 응답의 `*Count` 사용(클라이언트 교차계산 불필요).
- ⚠️ `FollowNode.href` 미제공 → 프론트는 이미 `https://www.instagram.com/${username}` 로 링크 생성하므로 영향 없음.
- `FollowNode.timestamp` ↔ `followedAt`(nullable).
- 잘못된 `type` → `400 VALIDATION_FAILED`.

### 4.3 DM 통계 — `GET /api/messages/stats`

응답 `ApiResponse<MessageStatsResponse>`:
```jsonc
{
  "data": {
    "totalRooms": 0,
    "totalMessages": 0,
    "topPartners": [
      { "partnerName": "...", "messageCount": 0, "sentCount": 0, "receivedCount": 0, "ownerInitiated": true }
    ],
    "hourlyDistribution": [/* int[24] */]
  }
}
```
**프론트 매핑 / 🔴 갭**:
- `ChatRoom.totalMessages/sentCount/receivedCount/name` ↔ `topPartners[*]` (단, **Top N=10** 만, 기본값 `instagram.message.top-n`).
- 🔴 **메시지 본문(`MessageNode.content/media_*`)·대화 로그·참여자 목록을 백엔드가 제공하지 않는다.** 현재 `MessagesView` 의 **대화 말풍선 복원 화면은 이 API로 구현 불가**.
  - 대안 A: DM 화면을 "통계 전용"(대화방 수, Top 파트너, 시간대 분포)으로 재설계.
  - 대안 B: 원문이 필요하면 `GET /api/explorer/file?path=messages/inbox/<room>/message_1.json` 으로 raw JSON을 받아 프론트에서 파싱(현 `parseChatRoom` 재사용).
- owner 미해결 시 → `409 DM_OWNER_NOT_RESOLVED` → §3.3 흐름.
- `hourlyDistribution` 으로 시간대 차트 렌더 가능.

### 4.4 활동 기록 — `GET /api/activity?type=post|like|comment|saved`

응답 `ApiResponse<ActivityResponse>`:
```jsonc
{
  "data": {
    "type": "post",
    "total": 0,
    "monthlyCounts": [ { "month": "2026-03", "count": 12 } ]  // yyyy-MM
  }
}
```
**프론트 매핑 / 🔴 갭**:
- 🔴 백엔드는 **월별 집계(메타데이터)만** 반환. `PostNode.caption/uri`, `LikeNode.target`, `CommentNode.content` 등 **개별 항목 내용은 제공하지 않는다.**
  - 현재 `ActivityView` 의 **항목 피드(캡션/좋아요 대상/댓글 내용 리스트)는 구현 불가** → "월별 추이 차트 + 총계" 화면으로 재설계 권장.
  - 원문이 필요하면 `/api/explorer/file` 로 raw 접근.
- ⚠️ 백엔드에 `saved`(저장됨) 타입이 추가로 존재(프론트 도메인엔 없음) — 신규 탭 추가 가능.
- 탭 카운트(`📸 쓴 게시물 (n)`)는 `total` 사용.

### 4.5 활동 히트맵 — `GET /api/heatmap`

응답 `ApiResponse<HeatmapResponse>`:
```jsonc
{
  "data": {
    "grid": [/* int[7][24] */],
    "peak": { "dayOfWeek": 2, "hour": 21, "count": 34 }
  }
}
```
**프론트 매핑 / ⚠️ 주의**:
- 현재 `lib/stats/activityStats.buildHeatmap` 의 클라이언트 계산을 이 응답으로 대체.
- ⚠️ **요일 인덱스 규약 불일치**: 백엔드 `dayOfWeek` 는 **0=월 … 6=일**, 프론트 히트맵 그리드/`DAYS` 배열은 **0=일 … 6=토**(JS `getDay()`). → **렌더 시 인덱스 리매핑 필수**.
- `peak` 로 "Peak Hour Insight" 카드 구성(`dayOfWeek`, `hour`, `count`).

### 4.6 검색 기록 — `GET /api/searches`

응답 `ApiResponse<SearchResponse>`:
```jsonc
{ "data": { "frequencies": [ { "keyword": "...", "count": 5 } ] } }   // 빈도 내림차순
```
- 데이터 없음 → `200` + `code="SEARCH_HISTORY_NOT_FOUND"`.
**프론트 매핑 / 🟡**:
- LogsView "최다 관심 키워드 Top 10" = `frequencies` 상위 10(프론트의 클라이언트 빈도 집계 제거).
- 🟡 `SearchNode.timestamp` 기반 **개별 검색 타임라인은 제공되지 않음** → "Search History Timeline" 섹션은 빈도 뷰로 축소하거나 `/api/explorer/file` 로 raw 접근.

### 4.7 로그인 기록 — `GET /api/logins`

응답 `ApiResponse<LoginResponse>`:
```jsonc
{
  "data": {
    "timeline": [
      { "timestamp": 1729452000000, "type": "LOGIN", "ipAddress": "112.x.x.x", "userAgent": "..." }
    ]
  }
}
```
- 데이터 없음 → `200` + `code="LOGIN_HISTORY_NOT_FOUND"`.
**프론트 매핑 / 🟡**:
- `LoginNode.timestamp/ip/userAgent` ↔ `timestamp/ipAddress/userAgent`.
- ⚠️ `LoginNode.device_ua`(기기 라벨), `LoginNode.location`(지리 정보)는 **미제공** → 기기 아이콘/라벨은 프론트가 `userAgent` 에서 파생(현 `deviceIcon` 로직 활용), **지도/지역 표시는 데이터 없음**.
- ⚠️ 백엔드는 `LOGOUT` 이벤트도 포함(`type`) — 프론트 모델은 로그인만 가정. 타입 표시/필터 추가 필요.

### 4.8 각종 로그 — `GET /api/logs`

응답 `ApiResponse<MiscLogResponse>`:
```jsonc
{
  "data": {
    "files": [
      { "fileName": "device_information.json", "rows": [ { "key1": "val1", "key2": "val2" } ] }
    ]
  }
}
```
- 디렉토리 없음 → `200` + `code="MISC_LOG_DIR_NOT_FOUND"`.
**프론트 매핑 / 🟡 형태 상이**:
- 백엔드: `파일 → 행(rows) → 키-값 맵` 구조(파일당 여러 행).
- 프론트 `MiscLogNode`: 단일 `{fileName, eventType, description, details, timestamp}`.
- ⚠️ `eventType`/`description`/`timestamp` 는 **백엔드 미제공** → 프론트는 `fileName` + `rows[*]`(키-값 그리드)만 렌더하도록 단순화 필요.

### 4.9 데이터 탐색기 — `GET /api/explorer/tree` · `GET /api/explorer/file?path=...`

`tree` 응답: `ApiResponse<{ root: ExplorerNode }>` (임포트 루트 기준 디렉토리 트리, 최대 깊이 `instagram.explorer.max-depth=10`).
`file` 응답:
```jsonc
{ "data": { "path": "messages/inbox/...", "content": "{...raw json...}", "truncated": false } }
```
- 최대 `instagram.explorer.max-file-bytes=10MB` 초과 시 `truncated:true`.
**프론트 매핑 / 🟢(목적 상이)**:
- 현재 `RawExplorer` 는 "붙여넣기→파싱" 샌드박스. 백엔드 탐색기는 **임포트된 폴더의 읽기 전용 브라우저**.
- 경로 위반 → `400 EXPLORER_PATH_OUT_OF_ROOT`, 파일 없음 → `404 EXPLORER_FILE_NOT_FOUND`, 미임포트 → `409 EXPLORER_NOT_IMPORTED`.
- **활용**: §4.3/4.4/4.6 의 "원문이 필요한" 화면에서 raw JSON 소스로 사용 + 프론트 파서(`parseChatRoom` 등) 재사용 가능.

---

## 5. 핵심 갭 요약 (프론트 재작업 필요 항목)

| # | 갭 | 영향 화면 | 권장 대응 |
|---|---|---|---|
| G-1 | **업로드 모델 상이** — 백엔드는 파일 업로드가 아니라 **서버 폴더 경로** ETL | Uploader | 드래그&드롭 → 경로 입력 + `status` 폴링 + owner fallback UX로 재설계 |
| G-2 | **DM 본문/대화 로그 없음** (통계만) | MessagesView | 통계 전용 화면으로 재설계 또는 Explorer raw로 원문 보강 |
| G-3 | **활동 개별 항목 없음** (월별 집계만) | ActivityView | 월별 추이 차트로 재설계 / `saved` 타입 추가 |
| G-4 | **검색 개별 타임라인 없음** (빈도만) | LogsView(검색) | 빈도 뷰로 축소 또는 Explorer raw |
| G-5 | **로그인 location/device_ua 없음, LOGOUT 포함** | LogsView(로그인) | userAgent 파생 라벨, 지도 제거, type 표기 |
| G-6 | **MiscLog 형태 상이** (파일→rows→맵) | LogsView(각종 로그) | fileName + 키-값 그리드로 단순화 |
| G-7 | **히트맵 요일 인덱스 규약 차이** (BE 0=월 / FE 0=일) | ActivityHeatmap | 렌더 시 인덱스 리매핑 |
| G-8 | **데모/`isDemo`/localStorage 개념은 백엔드에 없음** | 전역 상태 | 데모는 프론트 전용 유지 or 제거, 영속은 서버 상태로 이관 |
| G-9 | **Overview에 피크 *시각*·top partner 없음** | Overview | heatmap.peak / messages.topPartners 병합 조회 |

---

## 6. 프론트 적용 시 변경 포인트 (제안)

1. **`services/` 계층 교체**: `parsing/`·`demo/`·`storage/` → `services/api/` (HTTP 클라이언트 + 엔드포인트별 함수). 기존 `parser.ts` 는 Explorer raw 보강용으로만 잔존.
2. **상태 관리**: 단일 `InstagramDataState` + reducer → 화면별 서버 상태 캐시(예: TanStack Query). `useInstagramData()` 대신 `useOverview()`, `useFollows(type)` 등 쿼리 훅.
3. **에러 처리 공통화**: `ErrorResponse.code` 기준 분기 + `ApiResponse.code`(G4) 분기 유틸. 아래 표 참조.
4. **임포트 게이트**: `importRequired`/`IMPORT_NOT_COMPLETED(503)` 감지 → Uploader 라우팅.
5. **타입 정의**: 본 문서의 응답 스키마를 `domain/api/*` 타입으로 추가(기존 `domain/types.ts` 는 표현용으로 유지하되 API DTO와 분리).

### 6.1 에러 코드 → 프론트 처리 표

| code | HTTP | 처리 |
|---|---|---|
| `IMPORT_PATH_BLANK` / `OWNER_INPUT_BLANK` / `VALIDATION_FAILED` | 400 | 폼 인라인 에러 |
| `IMPORT_PATH_NOT_FOUND` / `IMPORT_PATH_NOT_DIRECTORY` | 400 | 경로 입력 에러 |
| `IMPORT_NOT_INSTAGRAM_EXPORT` / `IMPORT_HTML_ONLY` | 422 | "JSON 포맷 재다운로드" 안내 |
| `DM_OWNER_NOT_RESOLVED` | 409 | 본인 username 입력 모달(§3.3) |
| `IMPORT_REIMPORT_REQUIRED` | 409 | 재임포트 유도 |
| `IMPORT_NOT_COMPLETED` | 503 | Uploader로 라우팅 |
| `EXPLORER_PATH_OUT_OF_ROOT` | 400 | 탐색 차단 토스트 |
| `EXPLORER_FILE_NOT_FOUND` | 404 | 파일 없음 표시 |
| `EXPLORER_NOT_IMPORTED` | 409 | 임포트 유도 |
| `INTERNAL_ERROR` | 500 | 일반 오류 토스트 |
| `SEARCH_HISTORY_NOT_FOUND` / `LOGIN_HISTORY_NOT_FOUND` / `MISC_LOG_DIR_NOT_FOUND` | **200** | 에러 아님 — "데이터 없음" 빈 상태 UI |

---

## 7. 부록 — 엔드포인트 목록

| 메서드 | 경로 | 쿼리/바디 | 응답 타입 | envelope |
|---|---|---|---|---|
| POST | `/api/import` | body `{folderPath}` | `ImportStatusResponse` | ❌ 직접 |
| GET | `/api/import/status` | — | `ImportStatusResponse` | ❌ 직접 |
| POST | `/api/import/owner` | body `{username}` | `ImportStatusResponse` | ❌ 직접 |
| GET | `/api/overview` | — | `OverviewResponse` | ✅ ApiResponse |
| GET | `/api/follows` | `?type=ALL\|MUTUAL\|I_FOLLOW_ONLY\|FOLLOWS_ME_ONLY\|UNFOLLOWED\|PENDING\|CLOSE_FRIENDS\|RESTRICTED` | `FollowResponse` | ✅ |
| GET | `/api/messages/stats` | — | `MessageStatsResponse` | ✅ |
| GET | `/api/activity` | `?type=post\|like\|comment\|saved` | `ActivityResponse` | ✅ |
| GET | `/api/heatmap` | — | `HeatmapResponse` | ✅ |
| GET | `/api/searches` | — | `SearchResponse` | ✅ (G4 가능) |
| GET | `/api/logins` | — | `LoginResponse` | ✅ (G4 가능) |
| GET | `/api/logs` | — | `MiscLogResponse` | ✅ (G4 가능) |
| GET | `/api/explorer/tree` | — | `ExplorerTreeResponse` | ✅ |
| GET | `/api/explorer/file` | `?path=...` | `ExplorerFileResponse` | ✅ |

> 근거 소스: `src/main/java/com/instagram/analyze/api/**/*Controller.java`, `domain/**/dto/*.java`, `api/common/ApiResponse.java`, `api/error/{ErrorCode,ErrorResponse,GlobalExceptionHandler}.java`, `config/{WebConfig,InstagramProperties}.java`, `src/main/resources/application.yaml`.
