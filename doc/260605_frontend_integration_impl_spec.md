# 프론트엔드 ↔ 백엔드 연동 구현 명세 (상세)

> 작성: 2026-06-05 · 대상: `frontend/` 를 Spring REST API 에 연결하는 구현자(사람/세션)
> 상위 핸드오프: `doc/260605_frontend_integration_handoff.md`(요약·프롬프트). **이 문서는 그보다 상세한 단계별 구현 명세다.**
> 백엔드 계약 진실 소스: `src/main/java/com/instagram/analyze/api/**`, `domain/**/dto/*.java`

---

## 0. 현재 상태 (2026-06-05 검증)

프론트는 **100% 클라이언트 사이드 그대로**이며 백엔드와 전혀 연결돼 있지 않다. 검증 결과:
- `services/api/` 디렉토리 **없음**, `vite.config.ts` 프록시 **없음**
- 소스 전체에 `fetch(` · `/api/` · axios · XHR **0건**, 데이터 페칭 라이브러리 **없음**
- `Uploader.tsx` 는 여전히 `ingestFiles`(브라우저 파싱) → `applyData(patch)` → `localStorage`
- 모든 화면이 `useInstagramData()` 의 단일 `InstagramDataState` + `lib/stats/*` 클라 집계에 의존

→ 백엔드(15 엔드포인트, 실데이터 검증·79 tests)와 프론트는 **두 개의 분리된 앱**. 본 명세는 그 사이 연동 계층을 만든다.

### 0.1 백엔드는 신뢰 가능
실 1.9GB export E2E 검증 완료(following 390·DM 144,217·logins 105·search 9·owner 자동식별). 실데이터에서 드러난 파서 버그 4건(title username·검색 파일명·enum 대소문자·ISO 로그인)은 **이미 수정**. 따라서 프론트는 백엔드 응답을 그대로 신뢰하면 된다.

### 0.2 dev 실행
```
# 백엔드 (저장소 루트) — 실제 샘플 export 에 연결
./gradlew bootRun --args='--instagram.data.root=../data'   # :8374
# 프론트
cd frontend && npm run dev                                  # :5173 (+ §2 프록시)
```

---

## 1. 목표 아키텍처

```
[브라우저 React] ──/api 프록시──▶ [Spring :8374] ──▶ data/ 의 export (서버 디스크)
   services/api/         REST            ETL + 집계 조회
```

- 프론트의 `services/parsing|demo|storage` → **`services/api/`(HTTP 계층)로 교체**. `parsing/parser.ts` 만 Explorer raw 보강용으로 잔존.
- 전역 상태(`InstagramDataState` 단일 객체) → **화면별 서버 상태**(쿼리 훅). 라이브러리는 선택(단일 사용자라 경량 `fetch + useState/useEffect` 훅으로 충분, 원하면 TanStack Query).
- 일부 화면은 백엔드가 주는 형태에 맞춰 **재설계**(§5 갭).

### 1.1 디렉토리 변화
```
services/
  api/
    client.ts          # fetch 래퍼: envelope 언랩, code 기반 에러 throw, XHR 업로드
    endpoints.ts       # 엔드포인트별 함수(getOverview, getFollows, uploadExport ...)
    types.ts           # 백엔드 DTO 대응 TS 타입(domain/api 로 둬도 됨)
  parsing/parser.ts    # (잔존) Explorer raw JSON 파싱 재사용
  # demo/, storage/, parsing/ingest.ts → 제거 또는 dev 전용
hooks/                 # 또는 features/*/hooks
  useOverview.ts, useFollows.ts, useMessageStats.ts, useHeatmap.ts, useActivity.ts,
  useSearches.ts, useLogins.ts, useMiscLogs.ts, useImport.ts(업로드/폴링)
```

---

## 2. 통신 규약 (반드시 준수)

| 항목 | 값 |
|---|---|
| Base URL | `http://localhost:8374` |
| dev 연결 | **Vite 프록시 권장**(CORS 회피). `vite.config.ts` 에 추가: `server: { proxy: { '/api': 'http://localhost:8374' } }` |
| 인증 | 없음(단일 사용자 localhost). 헤더 불필요 |
| 직렬화 | Jackson `non_null` → **null 필드 키는 응답에서 빠짐**. 계약: 키 부재 == null |

### 2.1 조회 응답 envelope `ApiResponse<T>`
모든 **GET 조회**(9종)는 `{ code, message, data }`.
- 정상: `code=null, message=null, data=...`
- **데이터 없음(G4, HTTP 200)**: `code="SEARCH_HISTORY_NOT_FOUND"|"LOGIN_HISTORY_NOT_FOUND"|"MISC_LOG_DIR_NOT_FOUND"` + 빈 data. **에러 아님** → "데이터 없음" 빈 상태 UI.
- **예외**: 임포트/업로드 계열(`/api/import*`)은 **envelope 없이 DTO 직접 반환**.

### 2.2 에러 응답 `ErrorResponse` (4xx/5xx)
```jsonc
{ "code": "DM_OWNER_NOT_RESOLVED", "message": "...", "detail": "...", "httpStatus": 409 }
```
- **분기는 HTTP status 가 아니라 `code` 로** 한다.

---

## 3. 엔드포인트 ↔ 응답 타입 (백엔드 DTO 기준, 필드는 실제 DTO 확인)

| 메서드 · 경로 | 응답 형태 | envelope |
|---|---|---|
| `POST /api/import/upload` (multipart `file`) | `UploadStatusResponse{status,fileName,extractedEntries,targetPath,message}` | ❌ |
| `GET /api/import/upload/status` | `UploadStatusResponse` (status: IDLE\|SAVING\|EXTRACTING\|COMPLETED\|FAILED) | ❌ |
| `GET /api/import/candidates` | `{dataRoot, autoImportSingle, candidates:[{path,name,account,exportedAt}]}` | ❌ |
| `POST /api/import` (body `{folderPath}`) | `ImportStatusResponse{status,ownerResolved,durationMillis,parsedItemCount,warnings[]}` | ❌ |
| `GET /api/import/status` | `ImportStatusResponse` | ❌ |
| `POST /api/import/owner` (body `{username}`) | `ImportStatusResponse` | ❌ |
| `GET /api/overview` | `OverviewResponse{importRequired, data?{followerCount,followingCount,mutualCount,iFollowOnlyCount,totalPostCount,conversationCount,messageCount,likeCount,commentCount,activityFrom,activityTo,mostActiveMonth}}` | ✅ |
| `GET /api/follows?type=ALL\|MUTUAL\|I_FOLLOW_ONLY\|FOLLOWS_ME_ONLY\|UNFOLLOWED\|PENDING\|CLOSE_FRIENDS\|RESTRICTED` | `FollowResponse{followerCount,followingCount,mutualCount,iFollowOnlyCount,followsMeOnlyCount, items:[{username,followedAt}]}` | ✅ |
| `GET /api/messages/stats` | `MessageStatsResponse{totalRooms,totalMessages, topPartners:[{partnerName,messageCount,sentCount,receivedCount,ownerInitiated}], hourlyDistribution:int[24]}` | ✅ |
| `GET /api/activity?type=post\|like\|comment\|saved` | `ActivityResponse{type,total, monthlyCounts:[{month,count}]}` | ✅ |
| `GET /api/heatmap` | `HeatmapResponse{grid:int[7][24], peak:{dayOfWeek,hour,count}}` | ✅ |
| `GET /api/searches` | `SearchResponse{frequencies:[{keyword,count}]}` | ✅(G4) |
| `GET /api/logins` | `LoginResponse{timeline:[{timestamp,type,ipAddress,userAgent}]}` | ✅(G4) |
| `GET /api/logs` | `MiscLogResponse{files:[{fileName, rows:[{}]}]}` | ✅(G4) |
| `GET /api/explorer/tree` | `{root: ExplorerNode{name,relativePath,directory,children[]}}` | ✅ |
| `GET /api/explorer/file?path=` | `{path,content,truncated}` | ✅ |

> `?type=` 는 enum **대소문자 무시**(소문자 `post`·대문자 `POST` 모두 동작).

---

## 4. Phase 0 — API 인프라 (먼저)

### 4.1 `vite.config.ts` 프록시
```ts
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: { proxy: { '/api': 'http://localhost:8374' } },   // 추가
});
```

### 4.2 `services/api/client.ts`
```ts
export class ApiError extends Error {
  constructor(public code: string, public httpStatus: number, message: string) { super(message); }
}

// 조회: envelope 언랩 + code 기반 분기.
export async function getJson<T>(path: string): Promise<{ data: T; code: string | null }> {
  const res = await fetch(path);
  const body = await res.json();
  if (!res.ok) throw new ApiError(body.code ?? 'INTERNAL_ERROR', res.status, body.message ?? 'error');
  // 200 이지만 G4 code 가 있을 수 있음(데이터 없음) → code 를 함께 반환해 호출측이 빈 상태 처리
  return { data: body.data as T, code: body.code ?? null };
}

// 임포트/업로드: envelope 없이 직접 DTO
export async function postJson<T>(path: string, payload: unknown): Promise<T> {
  const res = await fetch(path, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload),
  });
  const body = await res.json();
  if (!res.ok) throw new ApiError(body.code ?? 'INTERNAL_ERROR', res.status, body.message ?? 'error');
  return body as T;
}

// 업로드: 진행률 필요 → fetch 가 아닌 XHR (fetch 는 업로드 progress 미지원)
export function uploadFile(path: string, file: File, onProgress: (pct: number) => void): Promise<unknown> {
  return new Promise((resolve, reject) => {
    const form = new FormData(); form.append('file', file);
    const xhr = new XMLHttpRequest();
    xhr.open('POST', path);
    xhr.upload.onprogress = (e) => { if (e.lengthComputable) onProgress(Math.round((e.loaded / e.total) * 100)); };
    xhr.onload = () => {
      const body = JSON.parse(xhr.responseText);
      if (xhr.status >= 200 && xhr.status < 300) resolve(body);
      else reject(new ApiError(body.code ?? 'INTERNAL_ERROR', xhr.status, body.message ?? 'upload failed'));
    };
    xhr.onerror = () => reject(new ApiError('INTERNAL_ERROR', 0, 'network error'));
    xhr.send(form);
  });
}
```

### 4.3 `services/api/endpoints.ts` (예시)
```ts
import { getJson, postJson, uploadFile } from './client';
import type { OverviewData, FollowResponse, /* ... */ } from './types';

export const api = {
  overview:       () => getJson<{ importRequired: boolean; data: OverviewData | null }>('/api/overview'),
  follows:        (type: string) => getJson<FollowResponse>(`/api/follows?type=${type}`),
  messageStats:   () => getJson<MessageStatsResponse>('/api/messages/stats'),
  activity:       (type: string) => getJson<ActivityResponse>(`/api/activity?type=${type}`),
  heatmap:        () => getJson<HeatmapResponse>('/api/heatmap'),
  searches:       () => getJson<SearchResponse>('/api/searches'),
  logins:         () => getJson<LoginResponse>('/api/logins'),
  logs:           () => getJson<MiscLogResponse>('/api/logs'),
  explorerTree:   () => getJson<{ root: ExplorerNode }>('/api/explorer/tree'),
  explorerFile:   (p: string) => getJson<ExplorerFile>(`/api/explorer/file?path=${encodeURIComponent(p)}`),
  // 임포트(직접 DTO)
  candidates:     () => fetch('/api/import/candidates').then(r => r.json()) as Promise<CandidatesResponse>,
  importStatus:   () => fetch('/api/import/status').then(r => r.json()) as Promise<ImportStatusResponse>,
  startImport:    (folderPath: string) => postJson<ImportStatusResponse>('/api/import', { folderPath }),
  resolveOwner:   (username: string) => postJson<ImportStatusResponse>('/api/import/owner', { username }),
  uploadStatus:   () => fetch('/api/import/upload/status').then(r => r.json()) as Promise<UploadStatusResponse>,
  uploadExport:   (file: File, onP: (n: number) => void) => uploadFile('/api/import/upload', file, onP),
};
```

### 4.4 `services/api/types.ts`
§3 표의 응답 형태를 그대로 TS interface 로 선언. 기존 `domain/types.ts`(FollowNode 등 표현용)는 **API DTO 와 분리**해 유지(점진 마이그레이션). nullable 은 `field?: T | null` 또는 키 부재로 표현.

### 4.5 폴링 유틸
```ts
export async function pollUntil<T>(fetcher: () => Promise<T>, done: (t: T) => boolean, intervalMs = 1000) {
  for (;;) { const v = await fetcher(); if (done(v)) return v; await new Promise(r => setTimeout(r, intervalMs)); }
}
```

**수용 기준(P0)**: `npm run build` 통과. 백엔드 띄운 상태에서 콘솔로 `api.overview()` 호출 시 실데이터 수신.

---

## 5. Phase 1 — Uploader 전면 재설계 (G-1, 최우선)

`features/uploader/Uploader.tsx` 를 **3단계 진행 UX** 로 교체. 기존 `ingestFiles`/드래그&드롭-클라파싱 제거.

진입 경로 3가지(모두 같은 ETL 합류):
1. **ZIP 업로드**(권장 메인): 사용자가 Instagram ZIP 선택 →
   - `api.uploadExport(file, setUploadPct)` → 업로드 진행바(XHR progress)
   - 응답 `status:"EXTRACTING"` → `pollUntil(api.uploadStatus, s => s.status==='COMPLETED'||s.status==='FAILED')` (extractedEntries 표시)
   - COMPLETED → 응답 `targetPath` 또는 `api.candidates()` 로 경로 확보 → 3번
   - ⚠️ 미디어 포함 전체 추출이라 `data/` 가 GB로 커짐 → "여유 디스크 필요" 안내
2. **자동 탐지**: 진입 시 `api.candidates()` → `candidates.length` 로 분기(0=수동입력 / 1&autoImportSingle=자동 / N=목록 선택)
3. **수동 경로**: 텍스트 입력 → 그 경로로 4

공통 마무리:
4. `api.startImport(folderPath)` → `pollUntil(api.importStatus, s => s.status==='COMPLETED'||s.status==='FAILED', 1000)` (parsedItemCount·durationMillis 진행 표시, warnings[] 안내)
5. COMPLETED → Overview 라우팅(`setActiveTab('overview')`). `ownerResolved===false` 면 DM 메뉴에서 username 입력 모달 필요(§5 DM).

전체 진행 UX = **업로드(XHR) → 추출(upload/status 폴링) → ETL(import/status 폴링) → Overview**. 실패는 각 status 의 `FAILED`+`message` 로 감지(HTTP 에러 아님).

**수용 기준(P1)**: 실 ZIP 업로드 → 추출 → 임포트 → Overview 진입까지 한 번에 동작.

---

## 6. Phase 2 — 화면별 연결 (현재 소비 → 대체)

> 모든 화면이 현재 `useInstagramData().data.*` + `lib/stats/*` 를 쓴다. 각 화면을 해당 쿼리 훅으로 교체.

| 화면(파일) | 현재 소비 | 대체 | 갭/주의 |
|---|---|---|---|
| **Overview** `overview/Overview.tsx`(+`hooks/useOverviewStats`) | `data.*` 전부 + `accountLifespan` | `api.overview()` + 피크는 `api.heatmap().peak` + 소울메이트는 `api.messageStats().topPartners[0]` | `importRequired:true` → Uploader 유도. 활동연령은 `activityFrom~activityTo` |
| **팔로우** `relations/Relations.tsx` | `data.followers/following/...` + `computeRelationBuckets` | `api.follows(type)` (7모드↔type 매핑 아래) | 카운트 배지는 응답 `*Count` 사용(클라 교차계산 제거). `href` 없음 → `https://instagram.com/${username}` 생성(현행 유지) |
| **DM** `messages/MessagesView.tsx` | `data.chatRooms[].messages`(말풍선) | `api.messageStats()` | 🔴 **본문 없음** → 말풍선/스플릿뷰 제거, **통계 화면 재설계**(방수·Top10·시간대). 원문 필요시 `api.explorerFile('.../message_1.json')`+`parseChatRoom` |
| **활동** `activity/ActivityView.tsx` | `data.posts/likes/comments` 개별 | `api.activity('post'\|'like'\|'comment'\|'saved')` | 🔴 **개별 항목 없음** → 피드 제거, **월별 추이 차트+총계**. 탭 카운트=`total`. `saved` 탭 추가 가능 |
| **히트맵** `activity/ActivityHeatmap.tsx` | `buildHeatmap(data)` 클라계산, `DAYS=['일요일'...]`(0=일) | `api.heatmap()` | ⚠️ **요일축 리매핑 필수**(§5 G-7 코드) |
| **검색** `logs/LogsView.tsx`(검색 섹션) | `data.searches` + 클라 빈도 | `api.searches().frequencies` | 빈도만(개별 타임라인 없음). G4 code → 빈 상태 |
| **로그인** `logs/LogsView.tsx`(로그인 섹션) | `data.logins` | `api.logins().timeline` | `location`/기기라벨 없음 → `userAgent` 파생, 지도 제거, `type`(LOGIN/LOGOUT) 표기. G4 |
| **각종 로그** `logs/LogsView.tsx`(로그 섹션) | `data.miscLogs`(평면) | `api.logs().files` | 형태 상이(파일→rows→맵) → fileName + 키-값 그리드. G4 |
| **탐색기** `explorer/RawExplorer.tsx` | "붙여넣기→파싱" 샌드박스 | `api.explorerTree()` / `api.explorerFile(path)` | 임포트 폴더 읽기전용 브라우저로. 경로위반 400/없음 404 |

### 6.1 팔로우 7모드 ↔ `type`
`dontFollowBack`→`I_FOLLOW_ONLY`, `dontFollowMe`→`FOLLOWS_ME_ONLY`, `mutual`→`MUTUAL`, `closeFriends`→`CLOSE_FRIENDS`, `unfollowed`→`UNFOLLOWED`, `pending`→`PENDING`, `restricted`→`RESTRICTED`.

---

## 7. Phase 3 — 정리
- `services/parsing/ingest.ts`·`services/demo/`·`services/storage/` 제거(또는 dev 데모 토글로만). `parser.ts` 는 Explorer raw 보강용 잔존.
- `state/instagramReducer.ts`·`InstagramDataProvider`·`useInstagramData` 의 단일 상태 → 쿼리 훅들로 대체(점진 가능: 먼저 훅 추가 → 화면 이관 → 단일 상태 제거).
- `lib/stats/*`(buildHeatmap·computeRelationBuckets 등 클라 집계) 중 백엔드가 대체한 것 제거. `format.ts`·UI 유틸은 유지.
- `isDemo`/localStorage 개념 제거 or dev 전용.

---

## 8. 갭 처리 상세 (G-1 ~ G-9)

- **G-1 업로드 모델**: §5 (경로 ETL/업로드, 파일업로드 아님).
- **G-2 DM 본문 없음**: 통계 전용 재설계 또는 explorer raw.
- **G-3 활동 개별 없음**: 월별 추이 차트.
- **G-4 검색 타임라인 없음**: 빈도 뷰.
- **G-5 로그인 location/기기 없음 + LOGOUT 포함**: userAgent 파생, 지도 제거, type 표기.
- **G-6 MiscLog 형태 상이**: fileName + rows 키-값 그리드.
- **G-7 히트맵 요일축**: ⚠️ 백엔드 `grid`/`peak.dayOfWeek` 는 **0=월…6=일**, 프론트 `DAYS=['일요일'…]` 는 **0=일…6=토**. 렌더 시 리매핑:
  ```ts
  // 백엔드 0=월..6=일 → 프론트 0=일..6=토 인덱스
  const beToFeDay = (be: number) => (be + 1) % 7;   // 월(0)→1, ... 일(6)→0
  // grid[beDay][hour] 를 프론트 행(feDay)으로 옮겨 렌더
  const feGrid = Array.from({length:7}, (_,fe)=> resp.grid[(fe+6)%7]); // fe=일(0)→be=일(6)
  // peak 표시: DAYS[beToFeDay(resp.peak.dayOfWeek)]
  ```
  (둘 중 하나로 일관 적용. 핵심: 월/일 0-기준 차이를 반드시 변환.)
- **G-8 데모/localStorage**: 프론트 전용 → 제거 or dev.
- **G-9 Overview 피크·top partner 없음**: heatmap.peak + messages.topPartners 병합.

---

## 9. 에러 코드 → 처리

| code | HTTP | 처리 |
|---|---|---|
| `IMPORT_PATH_BLANK`/`OWNER_INPUT_BLANK`/`VALIDATION_FAILED` | 400 | 폼 인라인 에러 |
| `IMPORT_PATH_NOT_FOUND`/`IMPORT_PATH_NOT_DIRECTORY` | 400 | 경로 입력 에러 |
| `IMPORT_NOT_INSTAGRAM_EXPORT`/`IMPORT_HTML_ONLY` | 422 | "JSON 포맷 재다운로드" 안내 |
| `DM_OWNER_NOT_RESOLVED` | 409 | 본인 username 입력 모달 → `api.resolveOwner` |
| `UPLOAD_IN_PROGRESS` | 409 | "업로드/추출 진행 중" — 잠시 후 재시도 |
| `IMPORT_REIMPORT_REQUIRED` | 409 | 재임포트 유도 |
| `IMPORT_NOT_COMPLETED` | 503 | Uploader 라우팅 |
| `EXPLORER_PATH_OUT_OF_ROOT` | 400 / `EXPLORER_FILE_NOT_FOUND` 404 | 탐색 차단 토스트 / 없음 표시 |
| `INTERNAL_ERROR` | 500 | 일반 오류 토스트 |
| `SEARCH_HISTORY_NOT_FOUND`/`LOGIN_HISTORY_NOT_FOUND`/`MISC_LOG_DIR_NOT_FOUND` | **200** | 에러 아님 — "데이터 없음" 빈 상태 |

---

## 10. 권장 순서 & 수용 기준
1. **P0 인프라**(§4) — build 통과 + overview 실데이터 수신
2. **P1 Uploader**(§5) — ZIP→추출→임포트→Overview 일괄 동작
3. **Overview**(§6, G-9) → **팔로우** → **히트맵**(G-7 리매핑) → **활동**(G-3) → **DM**(G-2) → **로그인/검색/로그**(G-4/5/6) → **탐색기**
4. **정리**(§7) — 미사용 클라 파싱/데모/스토리지 제거

각 화면 수용 기준: 백엔드 띄운 상태(실 export)에서 해당 화면이 실데이터를 정확히 표시 + 빈/에러 상태가 `code` 기준으로 분기.

## 11. 함정 요약
- 프론트는 디스크 직접 접근 불가 — 전부 API.
- 임포트는 경로 ETL/ZIP 업로드(파일 업로드 아님).
- 히트맵 요일축(0=월 vs 0=일) 리매핑 필수.
- DM 본문·미디어·활동 개별항목은 백엔드가 의도적으로 미제공 → 통계/메타 화면.
- 분기는 HTTP status 아닌 `code`.
- 백엔드 Boot 4 / Java 21, 실행 `./gradlew bootRun`(:8374), dev 는 `--instagram.data.root=../data`.
