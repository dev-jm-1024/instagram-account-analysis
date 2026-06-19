# 프론트엔드 ↔ 백엔드 연동 핸드오프 (프론트 담당 세션용)

> 작성: 2026-06-05 · 대상: 프론트엔드를 담당하는 별도 Claude Code 세션
> 이 문서 전체가 그 세션에 넘기는 프롬프트다. 아래 "=== 프롬프트 시작 ===" 부터 복사해 사용해도 된다.
> **더 상세한 단계별 구현 명세(코드 스켈레톤 포함): `doc/260605_frontend_integration_impl_spec.md`.**
> 상태(2026-06-05 확인): 프론트 연동 미시작 — services/api·프록시·fetch 전무, Uploader 클라파싱 그대로.

---

=== 프롬프트 시작 ===

## 현재 상태 (2026-06-05 기준)

- 백엔드는 **15개 엔드포인트 전부 구현 완료**이고, **실제 1.9GB export 샘플로 E2E 검증됨** — 너가 받는 데이터는 신뢰해도 된다(팔로우 390·맞팔 126·DM 144,217·로그인 105·검색 9 등 실값 확인). 실데이터에서 드러난 파서 버그 4건(따이틀-기반 username, 검색 파일명, enum 대소문자, ISO 로그인)은 **이미 수정**됐다.
- **ZIP 업로드(`POST /api/import/upload`)·data/ 자동탐지(`GET /api/import/candidates`)도 백엔드 구현 완료** — §4 흐름 그대로 호출만 하면 된다.
- **dev 실행**: 저장소 루트에서 `./gradlew bootRun --args='--instagram.data.root=../data'` → 8374 포트로 실제 샘플 export(`../data/instagram-myaccount...`)에 바로 붙여 개발 가능. 프론트는 `cd frontend && npm run dev`(5173) + vite 프록시(§2).
- `?type=` 쿼리는 enum 대소문자 무시라 소문자(`post`)·대문자(`POST`) 둘 다 동작한다.

## 너의 미션

이 저장소의 **React 프론트엔드(`frontend/`)를 Spring Boot 백엔드(`src/main/java`) REST API에 연동**한다.
현재 프론트는 100% 클라이언트 사이드(브라우저에서 raw JSON 파싱 → localStorage)이고, 백엔드는 서버 ETL + 집계 조회 API다. 프론트의 파싱/데모/스토리지 계층을 **API 호출 계층으로 교체**하고, 백엔드가 주는 데이터 형태에 맞춰 일부 화면을 재설계하는 것이 목표다.

**작업 방식**: 작은 청크 단위로 구현 → 빌드/타입체크(`cd frontend && npm run build`) → 사람 리뷰 → 승인 후 다음. 한 번에 다 갈아엎지 말 것. 기존 UI 컴포넌트(`components/ui/*`)와 디자인은 최대한 보존한다.

---

## 1. 먼저 읽어야 할 파일 (순서대로)

**백엔드 계약의 단일 진실 소스 (Java를 직접 읽어 DTO 필드명을 확인할 것):**
- `src/main/java/com/instagram/analyze/api/imports/ImportController.java` — 임포트 + `GET /api/import/candidates`
- `src/main/java/com/instagram/analyze/api/*/`*Controller.java` (overview/follow/message/activity/heatmap/search/login/log/explorer) — 13개 엔드포인트
- `src/main/java/com/instagram/analyze/domain/*/dto/*.java` — 모든 응답 DTO의 정확한 필드명/타입
- `src/main/java/com/instagram/analyze/api/common/ApiResponse.java`, `ApiResultCode.java` — 조회 응답 envelope + G4 코드
- `src/main/java/com/instagram/analyze/api/error/ErrorCode.java`, `ErrorResponse.java` — 에러 코드 ↔ HTTP ↔ 메시지
- `src/main/resources/application.yaml` — 포트(8374), `instagram.data.*`, CORS 설정

**참고 스펙 문서:**
- `doc/domain.md` — 도메인 V1.0 전체 명세 (10 도메인, 부록 A 엔드포인트표)
- `doc/domain_exception.md` — 예외 그룹 G1~G6, ErrorResponse 구조
- `doc/260604_data_scan_plan_v1.md` — **data/ 자동 스캔 설계 + §7 구현 현황** (candidates 엔드포인트는 이미 백엔드 구현 완료)

**네가 고칠 프론트 파일:**
- `frontend/src/state/{InstagramDataProvider.tsx, instagramReducer.ts, useInstagramData.ts, InstagramDataContext.ts}` — 전역 상태(현재 단일 InstagramDataState + reducer)
- `frontend/src/services/{parsing/*, demo/demoData.ts, storage/*}` — **교체 대상**(API 계층으로). `parsing/parser.ts`는 Explorer raw 보강용으로 잔존
- `frontend/src/features/uploader/Uploader.tsx` — **전면 재설계**(드래그&드롭 → 경로/후보 선택)
- `frontend/src/features/{overview, relations, messages, activity, logs, explorer}/*` + `features/overview/hooks/useOverviewStats.ts` + `lib/stats/*` — 클라이언트 집계 제거, API 응답 사용
- `frontend/src/domain/types.ts` — API DTO 타입 추가(표현용 기존 타입과 분리)
- `frontend/vite.config.ts` — dev 프록시 추가(아래 §2)

---

## 2. 통신 규약 (반드시 지킬 것)

- **Base URL**: `http://localhost:8374`. 프론트 dev: `http://localhost:5173`(Vite).
- **dev 연결 = Vite 프록시 권장**(CORS 회피). `vite.config.ts`에 추가:
  ```ts
  server: { proxy: { '/api': 'http://localhost:8374' } }
  ```
  (대안: 백엔드 `application.yaml`의 `instagram.web.cors.allowed-origins`에 `http://localhost:5173` 추가. 프록시가 더 간단.)
- **인증/세션/토큰 전부 없음** — 단일 사용자 로컬 앱. 헤더 불필요.
- **Jackson `non_null`**: null 필드 키는 응답에서 빠진다. 계약: **키 부재 == null**.
- **조회(GET) 응답은 envelope `ApiResponse<T>`**: `{ code, message, data }`.
  - 정상: `code=null, message=null, data=...`
  - **데이터 없음(G4, HTTP 200)**: `code="SEARCH_HISTORY_NOT_FOUND"|"LOGIN_HISTORY_NOT_FOUND"|"MISC_LOG_DIR_NOT_FOUND"` + 빈 data. **에러 아님** → "데이터 없음" 빈 상태 UI로 분기.
- **임포트 3종(`/api/import*`)은 envelope 없이 `ImportStatusResponse`를 직접 반환.** `GET /api/import/candidates`도 직접 반환(`CandidatesResponse`).
- **분기는 HTTP status가 아니라 `code`로** 한다(ErrorResponse.code / ApiResponse.code).

---

## 3. 엔드포인트 목록

| 메서드 | 경로 | 쿼리/바디 | 응답 | envelope |
|---|---|---|---|---|
| POST | `/api/import/upload` | multipart `file`(ZIP/JSON) | `UploadStatusResponse` `{status,fileName,extractedEntries,targetPath,message}` | ❌ 직접 |
| GET | `/api/import/upload/status` | — | `UploadStatusResponse` | ❌ 직접 |
| GET | `/api/import/candidates` | — | `CandidatesResponse` `{dataRoot, autoImportSingle, candidates:[{path,name,account,exportedAt}]}` | ❌ 직접 |
| POST | `/api/import` | `{folderPath}` | `ImportStatusResponse` `{status,ownerResolved,durationMillis,parsedItemCount,warnings[]}` | ❌ |
| GET | `/api/import/status` | — | `ImportStatusResponse` | ❌ |
| POST | `/api/import/owner` | `{username}` | `ImportStatusResponse` | ❌ |
| GET | `/api/overview` | — | `OverviewResponse` `{importRequired, data?}` | ✅ |
| GET | `/api/follows` | `?type=ALL\|MUTUAL\|I_FOLLOW_ONLY\|FOLLOWS_ME_ONLY\|UNFOLLOWED\|PENDING\|CLOSE_FRIENDS\|RESTRICTED` | `FollowResponse` | ✅ |
| GET | `/api/messages/stats` | — | `MessageStatsResponse` | ✅ |
| GET | `/api/activity` | `?type=post\|like\|comment\|saved` | `ActivityResponse` `{type,total,monthlyCounts[]}` | ✅ |
| GET | `/api/heatmap` | — | `HeatmapResponse` `{grid:int[7][24], peak:{dayOfWeek,hour,count}}` | ✅ |
| GET | `/api/searches` | — | `SearchResponse` | ✅ (G4) |
| GET | `/api/logins` | — | `LoginResponse` `{timeline:[{timestamp,type,ipAddress,userAgent}]}` | ✅ (G4) |
| GET | `/api/logs` | — | `MiscLogResponse` `{files:[{fileName,rows:[{}]}]}` | ✅ (G4) |
| GET | `/api/explorer/tree` | — | `ExplorerTreeResponse` | ✅ |
| GET | `/api/explorer/file` | `?path=...` | `ExplorerFileResponse` `{path,content,truncated}` | ✅ |

> 필드명은 추정하지 말고 위 DTO 파일들을 직접 열어 확인할 것.

---

## 4. 임포트 흐름 — Uploader 전면 재설계 (G-1, 최우선)

백엔드 임포트는 **파일 업로드가 아니라 서버 머신의 폴더 경로**를 받아 ETL 한다. 진입 경로는 **3가지**이며 모두 같은 ETL로 합류한다: ① 배포 시 `data/`에 미리 있는 export 자동 탐지, ② **ZIP 업로드**(브라우저→localhost→`data/` 저장+추출), ③ 수동 경로 입력.

**4-A. ZIP 업로드 경로 (Instagram에서 받은 ZIP 그대로)** — 권장 메인 UX
1. 사용자가 ZIP 파일 선택 → `POST /api/import/upload` (multipart, 필드명 `file`).
   - **업로드 진행률**은 `XMLHttpRequest`의 `upload.onprogress`로 표시(fetch는 업로드 진행률 미지원 → XHR 사용). 5GB도 브라우저가 디스크에서 스트리밍하므로 메모리 안전.
   - 응답: ZIP이면 `status:"EXTRACTING"` 즉시 반환(저장 완료, 추출은 비동기).
2. `GET /api/import/upload/status` **폴링** → `status`가 `COMPLETED`(또는 `FAILED`)까지. `extractedEntries`로 추출 진행 표시.
3. `COMPLETED` 되면 응답의 `targetPath`(또는 `GET /api/import/candidates`)로 export 경로 확보 → **4-B의 2번으로 합류**.
   - ⚠️ ZIP은 미디어 포함 전체 추출이라 `data/`가 GB 단위로 커진다 → "여유 디스크 공간 필요" 안내 한 줄 권장.

**4-B. 자동 탐지 / 수동 경로 경로**
1. 진입 시 `GET /api/import/candidates`.
   - `candidates.length === 0` → "data/에 export가 없습니다" + **수동 경로 입력** 또는 위 ZIP 업로드 유도.
   - `length === 1 && autoImportSingle` → 선택 UI 생략하고 바로 임포트.
   - `length >= 2` → 후보 목록(name/account/exportedAt) → **사용자가 1개 선택**.
2. 선택/입력/업로드된 `path`로 `POST /api/import { folderPath: path }`.
3. `GET /api/import/status` **폴링**(1초 간격) → `COMPLETED`/`FAILED`까지.
   - 진행 표시: `parsedItemCount`("12,340건 파싱"), `durationMillis`. `warnings[]`로 스킵 항목 안내.
   - `COMPLETED` → Overview. `ownerResolved === false`면 DM 메뉴에서 본인 username 입력 모달(§6 DM).
4. 에러는 `code`로 분기(§5).

> 전체 단계 = **업로드 진행률(XHR) → 추출 진행률(upload/status 폴링) → ETL 진행률(import/status 폴링) → Overview**. 3단계 진행 UX.
> 업로드 실패/추출 실패는 HTTP 에러가 아니라 `upload/status`의 `status:"FAILED"` + `message`로 온다(폴링으로 감지).

기존 드래그&드롭(클라 파싱)/데모 복원/localStorage 개념은 제거하거나 dev 전용으로만 남긴다.

---

## 5. 에러 코드 → 처리

| code | HTTP | 처리 |
|---|---|---|
| `IMPORT_PATH_BLANK`/`OWNER_INPUT_BLANK`/`VALIDATION_FAILED` | 400 | 폼 인라인 에러 |
| `IMPORT_PATH_NOT_FOUND`/`IMPORT_PATH_NOT_DIRECTORY` | 400 | 경로 입력 에러 |
| `IMPORT_NOT_INSTAGRAM_EXPORT`/`IMPORT_HTML_ONLY` | 422 | "JSON 포맷 재다운로드" 안내 |
| `DM_OWNER_NOT_RESOLVED` | 409 | 본인 username 입력 모달 → `POST /api/import/owner` |
| `IMPORT_REIMPORT_REQUIRED` | 409 | 재임포트 유도 |
| `IMPORT_NOT_COMPLETED` | 503 | Uploader로 라우팅 |
| `EXPLORER_PATH_OUT_OF_ROOT` | 400 | 탐색 차단 토스트 |
| `EXPLORER_FILE_NOT_FOUND` | 404 | 파일 없음 표시 |
| `INTERNAL_ERROR` | 500 | 일반 오류 토스트 |
| `SEARCH_HISTORY_NOT_FOUND`/`LOGIN_HISTORY_NOT_FOUND`/`MISC_LOG_DIR_NOT_FOUND` | **200** | 에러 아님 — "데이터 없음" 빈 상태 |

---

## 6. 화면별 갭 (G-2 ~ G-9) — 데이터 없는 화면은 재설계

- **G-2 DM (`MessagesView`)**: 백엔드는 **통계만**(대화방 수, Top10 파트너, 시간대 분포). **메시지 본문/대화 로그 없음** → 말풍선 복원 화면 불가. 통계 전용 화면으로 재설계. 원문이 꼭 필요하면 `GET /api/explorer/file?path=messages/inbox/<room>/message_1.json`로 raw 받아 기존 `parseChatRoom` 재사용. owner 미해결 시 `409 DM_OWNER_NOT_RESOLVED`.
- **G-3 활동 (`ActivityView`)**: **월별 집계(`monthlyCounts`)만**. 개별 게시물/좋아요/댓글 내용 없음 → 추이 차트 + 총계로 재설계. 백엔드에 `saved` 타입 추가됨(탭 추가 가능).
- **G-4 검색 (`LogsView` 검색)**: **빈도(`frequencies`)만**, 개별 타임라인 없음.
- **G-5 로그인 (`LogsView` 로그인)**: `location`/`device_ua` **미제공**, `LOGOUT` 이벤트 포함(`type`). userAgent에서 기기 라벨 파생(기존 `deviceIcon` 활용), 지도/지역 제거, type 표기.
- **G-6 각종 로그**: 형태 = `files:[{fileName, rows:[{key:value}]}]`. 기존 `MiscLogNode`(eventType/description/timestamp)와 다름 → fileName + 키-값 그리드로 단순화.
- **G-7 히트맵 (`ActivityHeatmap`)**: ⚠️ **요일 인덱스 규약 차이**. 백엔드 `dayOfWeek`/grid는 **0=월 … 6=일**, 프론트(JS `getDay()`/`DAYS`)는 **0=일 … 6=토**. **렌더 시 인덱스 리매핑 필수**. `peak`로 "가장 활발한 시간" 카드.
- **G-8**: 데모/`isDemo`/localStorage는 백엔드에 없음 → dev 전용 유지 or 제거.
- **G-9 Overview**: 피크 *시각*·top partner는 overview에 없음 → `/api/heatmap`의 `peak` + `/api/messages/stats`의 `topPartners[0]` 병합 조회.

**탈출구**: G-2/G-3/G-4의 "원문이 필요한" 부분은 전부 `GET /api/explorer/file`로 raw JSON 받아 기존 프론트 파서 재사용 가능.

---

## 7. 권장 구현 순서 (청크)

1. **API 인프라**: `vite.config.ts` 프록시 + `services/api/client.ts`(fetch 래퍼: envelope 언랩, `code` 기반 에러 throw) + `domain/api/*.ts` 응답 타입. 데이터 페칭은 경량 훅(`useOverview()`, `useFollows(type)` 등)으로. (TanStack Query 도입은 선택 — 단일 사용자라 직접 fetch + useState/useEffect도 충분.)
2. **Uploader 재설계** (§4) — candidates → import → status 폴링 → Overview.
3. **Overview** (§6 G-9) — `/api/overview` + heatmap.peak + messages.topPartners.
4. **팔로우** — `/api/follows?type=` (프론트 7모드 ↔ FollowQueryType 매핑). 카운트 배지는 응답 `*Count` 사용(클라 교차계산 제거).
5. **히트맵** (G-7 리매핑), **활동**(G-3), **DM**(G-2), **로그인/검색/각종로그**(G-4/5/6), **탐색기**.
6. 마지막에 `lib/stats/*`·`services/parsing|demo|storage`에서 더 이상 안 쓰는 클라 집계/파싱 제거(parser.ts는 Explorer 보강용만 잔존).

각 청크마다 `cd frontend && npm run build`로 타입체크 통과 + 백엔드 띄운 상태(실제 `data/` export 존재)로 동작 확인 후 리뷰 요청.

---

## 8. 함정 정리

- 프론트는 디스크를 직접 못 본다 — 모든 데이터는 백엔드 API로만.
- 임포트는 **경로 ETL**이지 파일 업로드가 아니다. dev에서 백엔드 `instagram.data.root` 기본은 `./data`인데 실제 샘플은 저장소의 `../data`(= `<repo>/../data`)에 있다. 백엔드를 `--instagram.data.root=../data` 등으로 띄우거나 수동 경로 입력으로 테스트.
- 히트맵 요일축 리매핑(0=월 vs 0=일) 빼먹지 말 것.
- DM 본문·미디어·활동 개별항목은 백엔드가 의도적으로 안 준다(프라이버시·용량). 통계/메타데이터 화면으로 간다.
- 백엔드는 Spring Boot 4 / Java 21. 실행: 루트에서 `./gradlew bootRun` (포트 8374).

=== 프롬프트 끝 ===
