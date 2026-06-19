# 도메인별 서비스(인터페이스) 설계안 v4

> 작성: 2026-06-04 15:31 (v4 갱신) · 기준: `doc/domain.md`, `doc/domain_exception.md`, `doc/domain_io.md`, 부록 A
> 선행: 도메인 모델 47개 (`task/domain/1530_domain_log.md`)
> 이력: v1 → v2(리뷰 A~E) → v3(상호작용 정정 A~E) → **v4(계약 문구 정정 F~H)**
> v4 변경: 시그니처 변경 없음 — `stats()` 계약·`importFrom` 논블로킹·Overview 활동기간 문구만. 맨 아래 표 참조

## 1. Context / 전제

도메인 모델 위에 **조회용 서비스 계층**을 인터페이스로 정의한다. 구현(@Service)은 다음 단계.

- **DB 없음 / 인메모리** — 파싱 결과는 단일 보관소에 적재, Java Stream 집계
- **읽기 전용** — 유일한 쓰기는 임포트(ETL) + 수동 본인식별(fallback)
- **단일 사용자 / localhost** — 세션·인증·동시성 제어 없음
- **상태 게이트** — 임포트 `COMPLETED`, 본인식별 `ownerResolved` 가 일부 서비스의 전제

## 2. 계층 / 패키지 구조

```
com.instagram.analyze
├── domain/...                  엔티티·VO·DTO  (DTO '정의'는 domain.x.dto 에 유지)
├── application/                서비스 인터페이스 = 순수 오케스트레이션
│   ├── store/                  ImportReadStore(읽기 포트) · ImportWritePort(쓰기 포트)
│   ├── support/                ImportGuard · Sourced<T>
│   ├── imports/  ImportService
│   ├── follow/ message/ activity/ heatmap/ search/ login/ log/ overview/ explorer/
├── infrastructure/             파싱·IO 협력자 (인프라)
│   ├── scan/ FileScanner   ├── text/ StringNormalizer
│   ├── identity/ AccountIdentityResolver   └── parse/ 도메인별 파서(후속)
└── api/                        Controller + Assembler
```

### 2.1 DTO 소유권 (B 확정)
- 정의는 `domain.x.dto` 유지. **생성**은 `api.Assembler` 단일 지점. api 중복 DTO 금지(이중 소유 방지).

### 2.2 반환 타입 규약
- 서비스는 **도메인 모델/집계**를 반환. `*Response` DTO 변환은 `api.Assembler` 가 담당.

## 3. 공통 횡단 관심사

### 3.1 보관소 — 읽기/쓰기 포트 분리 (ISP)

```java
// 읽기 포트 — 조회 서비스가 의존 (쓰기 메서드 안 보임)
public interface ImportReadStore {
    ImportResult importResult();
    boolean isCompleted();
    boolean isOwnerResolved();
    Optional<AccountIdentity> owner();

    List<FollowEntry> followEntries();
    List<Conversation> conversations();   // owner 미해결 시 owner-독립 필드만 채워진 상태 (§4.3)
    List<Post> posts();
    List<LikeEntry> likes();
    List<CommentEntry> comments();
    List<SavedPost> savedPosts();
    List<LoginEvent> loginEvents();
    List<SearchEntry> searchEntries();
    List<LogFile> logFiles();
    ActivityHeatmap heatmap();            // import 시점 사전계산본 (owner 무관)

    boolean sourceExists(DomainType domain); // primitive 신호 (§3.3 와 계층관계)
}

// 쓰기 포트 — ImportService 전용. 상태 전이도 store 단일 진실로 보유 (게이트·status() 일관성).
public interface ImportWritePort {
    void markInProgress();                        // importFrom 진입 → IN_PROGRESS (논블로킹)
    void markFailed(List<ParseWarning> warnings); // 백그라운드 치명오류 → FAILED
    void replaceAll(ImportSnapshot snapshot);     // 완료 → COMPLETED (원자 교체 = markCompleted)

    /**
     * 수동 본인식별 fallback 적용. conversations 교체뿐 아니라 부수효과로
     *  - store 의 owner 를 주어진 owner 로 채우고
     *  - ImportResult.ownerResolved 를 true 로 전환한다.
     */
    void applyOwner(AccountIdentity owner, List<Conversation> rebuiltConversations);
}
```
> 상태 전이(IDLE→IN_PROGRESS→COMPLETED/FAILED)가 모두 store 에 살아야 `ImportGuard` 와
> `ImportService.status()` 가 같은 진실을 본다. importFrom 논블로킹(§4.1)이 markInProgress 로 진입,
> 백그라운드 완료/실패가 replaceAll/markFailed 로 마감한다.

### 3.2 `support.ImportGuard` — 상태 게이트 (G3 분기 단일화)
```java
public interface ImportGuard {
    void requireCompleted();      // 미완료 → IMPORT_NOT_COMPLETED(503)        [히트맵 등]
    boolean isImportRequired();   // 미완료 → true (에러 아님, 200)             [개요]
    void requireOwnerResolved();  // 미해결 → throw OwnerNotResolved (하드 게이트) [DM]
}
```
> `requireOwnerResolved()` 는 **던지는** 게이트다(부분 응답 아님). F 참조(§4.3).

### 3.3 `support.Sourced<T>` — G4 "데이터 없음" 신호
```java
@Getter @AllArgsConstructor
public final class Sourced<T> {
    private final boolean sourceExists;  // false → Assembler가 *_NOT_FOUND code 부여
    private final T value;               // 없으면 빈 컬렉션 (null 아님)
}
```
**계층 관계 (D)**: 보관소는 primitive `ImportReadStore.sourceExists(DomainType)` 만 제공하고,
**서비스가** 그 boolean 으로 결과를 `Sourced<T>` 로 래핑해 API 계약화한다. (저수준 사실 → 고수준 계약, 중복 아님)

---

## 4. 도메인별 서비스 인터페이스

### 4.1 `imports.ImportService` — 임포트(1) / 유일한 쓰기 진입점
```java
public interface ImportService {
    ImportResult importFrom(String folderPath);   // POST /api/import  — 논블로킹(아래)
    ImportResult status();                         // GET  /api/import/status
    ImportResult resolveOwner(String username);    // POST /api/import/owner (수동 fallback)
}
```
**전제/예외 (G1·G2)**: blank→`IMPORT_PATH_BLANK`(400) / 미존재→`IMPORT_PATH_NOT_FOUND`(400) /
비디렉토리→`IMPORT_PATH_NOT_DIRECTORY`(400) / 패턴없음→`IMPORT_NOT_INSTAGRAM_EXPORT`(422) /
HTML만→`IMPORT_HTML_ONLY`(422) / 자동 식별 실패→`COMPLETED`+`ownerResolved:false` /
`resolveOwner` blank→`OWNER_INPUT_BLANK`(400) / 단일 파일 오류는 스킵+경고(G6).

**`importFrom` 은 논블로킹이다 (G 해소)**
- 동기 검증(경로·export 포맷, G1)만 **호출 스레드에서 즉시** 수행하고 통과하면 무거운 파싱은
  **백그라운드로 실행**한 뒤 **즉시 `ImportResult(status=IN_PROGRESS)` 를 반환**한다.
- 이후 프론트는 `GET /api/import/status` 를 **폴링**해 진행률·완료(`COMPLETED`/`FAILED`)를 받는다
  (domain.md 1절 "진행률을 프론트로 내려준다"가 성립하려면 importFrom 이 블로킹이면 안 됨).
- G1 검증 실패는 백그라운드 진입 **전에** 동기로 에러(400/422) 반환한다(상태가 IN_PROGRESS 로 가지 않음).
- 단일 사용자라 진행 중 재호출 경합은 사실상 없음(프론트가 버튼 비활성). 진행률 수치는 `ImportResult`/status 로 노출.

**`resolveOwner` 재파싱의 디스크 의존 (C)**
- 본안(재파싱)은 owner 확정 시 원본 export 폴더가 디스크에 잔존해야 함. 이동/삭제 시 재파싱 실패.
- 처리: **재파싱 실패 시 owner 확정 거부 + "재임포트하라" 안내**(재임포트 시 username 동반 → owner-의존 집계 한 번에).
- 트레이드오프(미채택): import 시 `(sender_name, timestamp_ms)` transient 보관 → 디스크 의존 제거, 메모리↑.

**협력자는 `infrastructure`**: `FileScanner`/`StringNormalizer`/`AccountIdentityResolver`.
Timestamp 정규화는 `EpochMillis.normalize()` VO 가 캡슐화. `ImportSnapshot` 은 스캔된 message 파일 경로를 함께 보관.

### 4.2 `follow.FollowService` — 팔로우(2)
```java
public interface FollowService {
    FollowAnalysis analyze();                          // 맞팔(∩)·짝사랑(차집합)
    List<FollowEntry> findByType(FollowQueryType type); // GET /api/follows?type=...
}
```
- `FollowQueryType`(신규): MUTUAL, I_FOLLOW_ONLY, FOLLOWS_ME_ONLY, UNFOLLOWED, PENDING, CLOSE_FRIENDS, RESTRICTED, ALL
- `Username` 소문자 정규화 equals. 결과 `FollowEntry`(시각 보존). 전제: `requireCompleted()`

### 4.3 `message.MessageService` — DM(3)  ★ owner-first, 하드 게이트 (F 핵심)
```java
public interface MessageService {
    MessageStats stats();   // GET /api/messages/stats
}
```
- owner-독립 집계(totalRooms·totalMessages·partnerName·hourlyDistribution[24])는 import 시 항상.
- owner-의존 집계(sentCount·receivedCount·ownerInitiated)는 owner 확정 시에만 채움.
- 미해결 import → owner-의존 필드 비움 + `ownerResolved:false`. 수동 `resolveOwner()` → message 재파싱 →
  `ImportWritePort.applyOwner(owner, rebuilt)` 로 교체(부수효과 §3.1).

**owner 미해결 동작 = throw (부분 응답 아님, F 해소)**
- `stats()` 전제는 `requireCompleted()` + `requireOwnerResolved()` 이며, 미해결이면 **`OwnerNotResolved`
  신호를 던진다**. owner-독립 통계만 담은 **부분 응답을 반환하지 않는다.**
- domain_exception 4.1 의 "보류"는 **프론트가 호출 전에 거는 사전 게이트**(owner 입력을 먼저 요구 →
  해결된 뒤에만 `stats()` 호출)이지, `stats()` 의 부분 응답 경로가 아니다.
- 따라서 구현자는 "owner-독립만 채운 MessageStats 반환" 같은 경로를 만들지 않는다.
- 히트맵 DM 기여분은 timestamp 만 쓰므로 owner 무관.

### 4.4 `activity.ActivityService` — 활동(4)
```java
public interface ActivityService {
    long count(ActivityType type);                          // post=post+story+reels 합산
    Map<YearMonth, Long> monthlyCounts(ActivityType type);  // 타입별 월별 카운트
    List<Post> posts();
    List<LikeEntry> likes();
    List<CommentEntry> comments();
    List<SavedPost> savedPosts();
}
```
- `ActivityType`(신규): POST, LIKE, COMMENT, SAVED. 전제: `requireCompleted()`
- `monthlyCounts` 는 **타입별**. Overview 의 `mostActiveMonth`(전 활동 합산 월)는 OverviewService 가 4타입 머지(§4.9).

### 4.5 `heatmap.HeatmapService` — 히트맵(5)
```java
public interface HeatmapService { ActivityHeatmap heatmap(); }   // GET /api/heatmap
```
- 전제: `requireCompleted()` → 미완료 `IMPORT_NOT_COMPLETED`(503). 5종 timestamp import 시 합산·캐싱(DM도 owner 무관).

### 4.6 `search.SearchService` — 검색(6)
```java
public interface SearchService { Sourced<List<SearchFrequency>> frequencies(); }
```
- `sourceExists=false` → `SEARCH_HISTORY_NOT_FOUND`(200, G4). `logged_information/` 검색 파일은 검색이 먼저 claim.

### 4.7 `login.LoginService` — 로그인(7)
```java
public interface LoginService { Sourced<List<LoginEvent>> timeline(); }   // 최신순
```
- `sourceExists=false` → `LOGIN_HISTORY_NOT_FOUND`(200, G4). ip/user_agent 문자열 그대로.

### 4.8 `log.MiscLogService` — 각종 로그(8)
```java
public interface MiscLogService { Sourced<List<LogFile>> logs(); }
```
- `sourceExists=false`(= `logged_information/` 미존재) → `MISC_LOG_DIR_NOT_FOUND`(200, G4). 검색이 claim 안 한 나머지만.

### 4.9 `overview.OverviewService` — 개요(9)  ★ A·B·E·H
```java
public interface OverviewService { OverviewSummary overview(); }   // GET /api/overview
```
**호출 순서 제약 (A)**
1. `overview()` 는 **맨 먼저** `guard.isImportRequired()` 확인 → true 면 즉시
   `OverviewSummary(importRequired=true, ...)` **early-return**.
2. 이 분기 **이전엔 게이트 걸린 형제 서비스를 호출하지 않는다**(503 누수 방지).

**집계 소스 (B·E·H)**
- `mutualCount` 등 팔로우 수치 → `FollowService.analyze()` **재사용**(맞팔 계산 단일화).
- DM 카드(대화방 수·총 메시지)는 owner-독립이지만 `stats()` 는 owner-게이트라 **재사용 불가** →
  `ImportReadStore.conversations()` 에서 **직접** 카운트(stats() 우회).
- `mostActiveMonth` → `ActivityService.monthlyCounts()` 4타입을 **Overview 가 머지**해 월별 합산 최댓값.
- **`activityFrom`/`activityTo`(활동 기간) → Overview 가 산출 (H 해소)**: store 가 보유한 **`EpochMillis`
  를 가진 모든 Timestamped 소스의 min/max** = 게시물·좋아요·댓글·저장·팔로우·검색·**로그인**(LoginEvent 는
  ts 보유 → 포함). **DM 은 제외** — 원시 메시지 ts 를 보관하지 않고 hourlyDistribution[24] 만 남기기
  때문(히트맵과 같은 제약). DM 까지 포함하려면 import 시 DM ts 의 min/max 만 사전계산해 store/ImportSnapshot
  에 노출해야 한다(heatmap 과 동일 패턴, 본안은 DM 제외로 단순화).

### 4.10 `explorer.ExplorerService` — 탐색기(10)
```java
public interface ExplorerService {
    ExplorerNode tree();                  // GET /api/explorer/tree (미디어 제외, 깊이 10)
    RawFileContent file(String path);     // GET /api/explorer/file?path=...
}
```
- 전제/예외(G5): 루트 외부→`EXPLORER_PATH_OUT_OF_ROOT`(400) / 미존재→`EXPLORER_FILE_NOT_FOUND`(404).
- 구조 raw 보존, 문자열만 `StringNormalizer` 보정. 10MB 초과 시 truncate.

---

## 5. 신규 보조 타입

| 타입 | 위치 | 용도 |
|---|---|---|
| `FollowQueryType` (enum) | `domain.follow` | follows 필터 분기 |
| `ActivityType` (enum) | `domain.activity` | activity 타입 분기 |
| `ImportSnapshot` | `application.store` | 임포트 산출물 묶음(컬렉션+heatmap+owner+경고+스캔 message 경로) |
| `Sourced<T>` | `application.support` | G4 "소스 존재 여부" 신호 래퍼 |

## 6. 의존 관계

```
ImportService ──writes──▶ ImportWritePort ─┐
   │ (infrastructure 협력자)                ├─▶ [인메모리 보관소]
   └ FileScanner·StringNormalizer·          │
     AccountIdentityResolver                │
조회 서비스 ──reads──▶ ImportReadStore ◀────┘   (쓰기 메서드 안 보임)
조회 서비스 ──게이트── ImportGuard

importFrom ─동기검증(G1)─▶ [통과] 백그라운드 파싱 + 즉시 IN_PROGRESS 반환 ─▶ status() 폴링
                          [실패] 즉시 400/422 (IN_PROGRESS 진입 안 함)

OverviewService:
   1) isImportRequired()? ─true─▶ importRequired 응답 (형제 호출 전 early-return)
   2) false ─▶ FollowService.analyze() 재사용 + conversations() 직접(DM, stats 우회)
              + monthlyCounts() 4타입 머지(mostActiveMonth)
              + 전 EpochMillis 소스 min/max(activityFrom/To, DM 제외)

MessageService.stats() ─owner 미해결─▶ throw OwnerNotResolved (부분 응답 없음)

resolveOwner ─재파싱(message 경로)─▶ applyOwner(owner, rebuilt)  [owner·ownerResolved 부수효과]
              └─ 폴더 잔존 안 함 ─▶ owner 확정 거부 + 재임포트 안내

api.Controller ──Assembler──▶ domain.x.dto  (store.sourceExists → Sourced<T> → code 부여)
```

## 7. v3 → v4 변경점 (전부 문구, 시그니처 불변)

| # | 지적 | 반영 |
|---|---|---|
| F | `stats()` owner 미해결 동작이 throw vs 부분응답 모순 | `stats()`는 미해결 시 **throw**, "보류"는 프론트 사전 게이트이며 부분 응답 없음 명시 (§3.2·§4.3) |
| G | 동기 importFrom 이면 status() 폴링 무력화 | `importFrom` **논블로킹**: 동기검증 후 백그라운드 파싱 + 즉시 IN_PROGRESS 반환, status() 폴링 (§4.1) |
| H | §4.9 활동기간(activityFrom/To) 산출 책임 누락 | Overview 가 전 EpochMillis 소스 min/max 산출, **로그인 포함·DM 제외**(원시 ts 미보관) 명시 (§4.9) |

## 8. 후속 / 검증

**진행 상태**: ✅ 설계 확정(리뷰 승인).
- **① 완료** — 보조 타입 4개 (`FollowQueryType`, `ActivityType`, `Sourced<T>`, `ImportSnapshot`).
- **②는 2a/2b 로 분할 진행** (코어 포트가 조회 서비스 9종의 의존 기반이라 먼저 확정·검토).
  - **2a 완료** — `compileJava` 통과. 코어 7개:
    `store.ImportReadStore` · `store.ImportWritePort` · `support.ImportGuard` · `imports.ImportService`
    + `infrastructure` 협력자 3개(`scan.FileScanner` · `text.StringNormalizer` · `identity.AccountIdentityResolver`).
  - ①산출물 리뷰 2건 흡수: `FollowQueryType` javadoc "의미 대응(이름 다름, 매핑 필요)"로 정정 /
    `ImportSnapshot` 이 메타 6필드 대신 `ImportResult` 통째 보유(중복 제거).
  - 2a 리뷰 반영: `ImportWritePort` 에 **전이 메서드 2개**(`markInProgress`/`markFailed`) 추가 —
    IN_PROGRESS/FAILED 도 store 단일 진실로 보유(게이트·status 일관성). 부수 결정:
    진행률은 **count 기반**(분모 없음) / `heatmap()` 미완료 시 **빈 7×24 반환**(NPE 회피).
  - **2b 완료** — `compileJava` 통과. 조회 서비스 9종:
    `follow.FollowService` · `message.MessageService` · `activity.ActivityService` · `heatmap.HeatmapService`
    · `search.SearchService` · `login.LoginService` · `log.MiscLogService` · `overview.OverviewService`
    · `explorer.ExplorerService`.
  - 2a 선택노트 반영: `ImportWritePort.applyOwner` javadoc 에 "전제: COMPLETED 상태" 추가.
- **③는 3a/3b 로 분할 진행.**
  - **3a 완료** — `compileJava` + Spring 컨텍스트 로딩 테스트 통과. 코어 구현 5개:
    `store.InMemoryImportStore`(읽기/쓰기 포트 한 빈, volatile 발행) · `support.DefaultImportGuard`
    · `imports.StubImportService`(동기 즉시완료 stub) · `support.ImportNotCompletedException`
    · `support.OwnerNotResolvedException`.
  - 2b 리뷰 doc 공백 2건 흡수: `FollowQueryType.ALL`=전 타입 합집합 명시 / `ExplorerService` 전제
    `requireCompleted()` 명시. 이를 위해 `ImportReadStore.importRoot()` 추가 +
    `markInProgress(Path importRoot)` 로 루트 캡처.
  - 부수: 게이트 예외 클래스 생성(HTTP 매핑은 §9대로 배선 단계에서). `importFrom` stub 은
    markInProgress→replaceAll 을 동기 압축(④에서 실제 ETL 로 교체).
  - **3b 완료** — `compileJava` + 전체 테스트 13개 통과(실패·스킵 0). 조회 서비스 9종 **실제 집계 구현**:
    Follow(집합연산)·Activity(월별)·Message(Top10·시간대)·Heatmap(passthrough)·Search/Login/MiscLog(Sourced)
    ·Overview(early-return+형제 재사용+DM직접+4타입머지+min/max)·Explorer(디스크 walk, 미디어제외·G5).
    빈 store 면 빈 결과 → ④ 에서 파서만 채우면 동작.
  - 3a 리뷰 watch-item 반영: (A) Heatmap null-peak 계약+테스트 / (B) Explorer 미임포트
    `ExplorerNotImportedException`(방어) / (C) 완료 가드 전 서비스 적용 / G5 예외 2개.
  - 슬라이스 테스트: `ServiceGatesTest`(8) + `ExplorerServiceTest`(4) — importFrom 전이·overview
    importRequired·heatmap 503·stats throw·Sourced.absent·null-peak·미디어제외·G5 커버.
- **API 계층은 API-a/API-b 로 분할.**
  - **API-a 완료** — `compileJava` + 테스트 16개 통과. 예외 배선 + 임포트 엔드포인트:
    `api.error.ErrorResponse`/`ErrorCode`(domain_exception §9 단일정의)/`GlobalExceptionHandler`(@RestControllerAdvice)
    + `api.imports.ImportController`(POST /api/import·GET status·POST owner) + `ImportAssembler`.
    - 예외 매핑 빚 정리: application 예외(ImportNotCompleted·OwnerNotResolved·Explorer 3종)→HTTP,
      @Valid 필드→IMPORT_PATH_BLANK/OWNER_INPUT_BLANK. `domain_exception.md` 에 신규 코드 2개
      (`DM_OWNER_NOT_RESOLVED` 409, `EXPLORER_NOT_IMPORTED` 409) 추가 → §9 추적 항목 종결.
    - Overview 하드닝(노트 3·4): activityFrom/To 단일 패스, mostActiveMonth 동점 시 최근 월(결정적).
    - 테스트: `ImportControllerTest`(@SpringBootTest+MockMvc) — 200 COMPLETED / blank→400 IMPORT_PATH_BLANK / owner blank→400.
  - **API-b 완료** — `compileJava` + 테스트 21개 통과. 조회 컨트롤러 9 + Assembler 9 + 공통 envelope:
    `api.common.ApiResponse<T>`(code/message/data) + `ApiResultCode`(G4 200 코드, ErrorCode 와 분리).
    Follow/Message/Activity/Heatmap/Search/Login/MiscLog/Overview/Explorer 컨트롤러+Assembler(DTO 생성 유일 지점).
    - 리뷰 반영: IllegalState(applyOwner)→`ImportNotCompletedException`(500 누수 차단) /
      G4 코드를 `ErrorCode` 에서 `ApiResultCode` 로 분리("200짜리 ErrorCode" 해소) /
      enum 파라미터 오타→`MethodArgumentTypeMismatchException` 핸들러(400).
    - 테스트: `ReadControllersTest`(@DirtiesContext, 5) — heatmap 503 / overview importRequired /
      search G4 / follows ok envelope / 잘못된 type 400.
- 다음: ④ 실제 파서(infrastructure: FileScanner·StringNormalizer·AccountIdentityResolver + 도메인 파서) 구현 →
  StubImportService 를 실제 ETL 로 교체(@Primary/제거). 이때 G1 경로/포맷 검증 예외(NOT_FOUND·NOT_INSTAGRAM_EXPORT 등) throw·매핑 추가.
- 검증: 인터페이스만으로 `compileJava` 통과 / stub 슬라이스 테스트로
  ▸ importFrom 즉시 IN_PROGRESS + status 폴링으로 COMPLETED 전이
  ▸ 미완료 시 overview 200(형제 미호출) ▸ owner 미해결 시 stats() throw / overview DM 카운트 정상(stats 우회)
  ▸ 히트맵 503 ▸ G4 sourceExists ▸ resolveOwner 재파싱 실패 분기 ▸ activityFrom/To 가 DM 제외·로그인 포함

## 9. 추적 항목 (비블로킹 — ②~③ 예외 핸들러 배선 시 처리)

- **`OwnerNotResolved` 의 HTTP 매핑이 `domain_exception.md` 에 없음.**
  §4.3 에서 `stats()` 가 owner 미해결 시 `OwnerNotResolved` 를 던지기로 확정했으나, domain_exception 은
  `ownerResolved:false` 를 **플래그(4.1)** 로만 다뤘고 이 throw 의 code·HTTP status 가 미정의.
  - 논리상 프론트가 사전 게이트하므로 서버까지 안 오지만, throw 가 존재하는 이상 방어적으로 도달했을 때
    (프론트 버그·직접 API 호출) 매핑이 필요.
  - **할 일**: 예외 매핑 테이블(ControllerAdvice) 작성 시 `domain_exception.md` 에 한 줄 추가.
    예: `DM_OWNER_NOT_RESOLVED` → **409 Conflict**(또는 422 Unprocessable Entity).
  - 지금 인터페이스를 막지 않음 — ②(인터페이스) 이후 ③ 사이의 예외 핸들러 배선 단계에서 채운다.
