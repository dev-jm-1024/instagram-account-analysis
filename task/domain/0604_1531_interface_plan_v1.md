# 도메인별 서비스(인터페이스) 설계안 v1

> 작성: 2026-06-04 15:31 · 기준: `doc/domain.md`, `doc/domain_exception.md`, `doc/domain_io.md`, 부록 A(REST)
> 선행: 도메인 모델 47개 생성 완료 (`task/domain/1530_domain_log.md`)

## 1. Context / 전제

도메인 모델 위에 **조회용 서비스 계층**을 인터페이스로 정의한다. 구현(@Service)은 다음 단계.
설계를 지배하는 전제(domain.md):

- **DB 없음 / 인메모리** — 파싱 결과는 단일 `@Service` 보관소에 적재, Java Stream 집계
- **읽기 전용** — 유일한 쓰기 시점은 임포트(ETL). 나머지 서비스는 전부 조회(GET)
- **단일 사용자 / localhost** — 세션·인증·동시성 제어 없음
- **상태 게이트** — 임포트 `COMPLETED` 여부, 본인식별 `ownerResolved` 여부가 일부 서비스의 전제

### 1.1 인터페이스를 두는 이유 (이 앱 맥락)
- 파싱 소스(YAML 매핑·export 버전 2020/2025)가 바뀌어도 **조회 서비스 시그니처는 불변** 유지
- 구현을 메모리 stub ↔ 실제 파서로 갈아끼우며 **컨트롤러/프론트 우선 개발** 가능
- 단위 테스트에서 가짜 데이터 주입 용이

## 2. 계층 / 패키지 구조 제안

```
com.instagram.analyze
├── domain/...                 (이미 생성: 엔티티·VO·DTO)
├── application/               (← 이번 설계 대상: 서비스 인터페이스)
│   ├── store/                 ImportStore (인메모리 보관소)
│   ├── support/               ImportGuard (상태 게이트), Assembler 규약
│   ├── imports/               ImportService + 파싱 협력자(FileScanner 등)
│   ├── follow/   FollowService
│   ├── message/  MessageService
│   ├── activity/ ActivityService
│   ├── heatmap/  HeatmapService
│   ├── search/   SearchService
│   ├── login/    LoginService
│   ├── log/      MiscLogService
│   ├── overview/ OverviewService
│   └── explorer/ ExplorerService
└── api/...                    (후속: Controller + DTO 매핑)
```

**반환 타입 규약**: 서비스는 **도메인 모델/집계**(`FollowAnalysis`, `MessageStats`, `ActivityHeatmap`,
`List<LoginEvent>` 등)를 반환한다. `*Response` DTO 변환은 `api` 계층의 얇은 Assembler가 담당한다.
(도메인 중심 유지 + 이미 만든 DTO를 API 계약으로 사용)

## 3. 공통 횡단 관심사

### 3.1 `store.ImportStore` — 인메모리 단일 보관소
파싱 결과를 들고 있고 모든 조회 서비스가 여기서 읽는다. 재임포트 시 통째 교체(덮어쓰기).

```java
public interface ImportStore {
    // 쓰기 (임포트 ETL 전용, 원자 교체)
    void replaceAll(ImportSnapshot snapshot);
    void updateOwner(AccountIdentity owner);   // 수동 본인식별 fallback

    // 상태
    ImportResult importResult();               // status, ownerResolved, 경고 등
    boolean isCompleted();
    boolean isOwnerResolved();

    // 도메인별 읽기 (원본 컬렉션)
    List<FollowEntry> followEntries();
    List<Conversation> conversations();
    List<Post> posts();
    List<LikeEntry> likes();
    List<CommentEntry> comments();
    List<SavedPost> savedPosts();
    List<LoginEvent> loginEvents();
    List<SearchEntry> searchEntries();
    List<LogFile> logFiles();
    ActivityHeatmap heatmap();                 // 임포트 시점 사전계산본 (5절)
    Optional<AccountIdentity> owner();
}
```
> `ImportSnapshot` = 임포트 산출물 묶음(위 컬렉션 + heatmap + owner + 경고). 신규 보조 타입(§5).

### 3.2 `support.ImportGuard` — 상태 게이트
임포트 미완료 시 도메인별로 다르게 처리(domain_exception G3)하는 분기를 한 곳에 모은다.

```java
public interface ImportGuard {
    void requireCompleted();        // 미완료면 IMPORT_NOT_COMPLETED(503) — 히트맵 등
    boolean isImportRequired();     // 개요용: 미완료면 true (에러 아님, 200)
    void requireOwnerResolved();    // 미해결이면 OwnerNotResolved 신호 — DM 통계
}
```

---

## 4. 도메인별 서비스 인터페이스

### 4.1 `imports.ImportService` — 임포트(1) / ETL 진입점 (유일한 쓰기)
**책임**: 경로 검증 → 스캔 → 스트리밍 파싱·정규화 → 본인식별 → `ImportStore` 적재 → 히트맵 사전계산.

```java
public interface ImportService {
    ImportResult importFrom(String folderPath);   // POST /api/import
    ImportResult status();                         // GET  /api/import/status
    ImportResult resolveOwner(String username);    // POST /api/import/owner (수동 fallback)
}
```
**전제/예외 (G1·G2)**
- `folderPath` blank → `IMPORT_PATH_BLANK`(400) / 미존재 → `IMPORT_PATH_NOT_FOUND`(400) / 디렉토리 아님 → `IMPORT_PATH_NOT_DIRECTORY`(400)
- export 패턴 없음 → `IMPORT_NOT_INSTAGRAM_EXPORT`(422) / HTML만 → `IMPORT_HTML_ONLY`(422)
- 자동 본인식별 실패 → `COMPLETED` + `ownerResolved:false` (에러 아님)
- `resolveOwner` username blank → `OWNER_INPUT_BLANK`(400)
- 단일 파일 파싱 오류는 스킵 + 경고 누적(G6), 전체 중단 안 함

**파싱 협력자 인터페이스 (1.1~1.4 분해)** — ImportService 내부 의존
```java
public interface FileScanner {            // 1.1
    Map<DomainType, List<Path>> scan(Path root);  // glob 매칭, 미디어/0바이트 제외
}
public interface StringNormalizer {       // 1.2  ISO-8859-1 → UTF-8 (mojibake 보정)
    String normalize(String raw);
}
public interface AccountIdentityResolver { // 1.4
    Optional<AccountIdentity> resolve(Map<DomainType, List<Path>> scanned);
}
```
> Timestamp 정규화(1.3)는 `EpochMillis.normalize()` VO가 이미 캡슐화 → 별도 인터페이스 불필요.

---

### 4.2 `follow.FollowService` — 팔로우(2)
**책임**: following/followers 교차 분석 + type 필터 조회.

```java
public interface FollowService {
    FollowAnalysis analyze();                         // 맞팔(∩)·짝사랑(차집합)
    List<FollowEntry> findByType(FollowQueryType type); // GET /api/follows?type=...
}
```
- `FollowQueryType`(신규 §5): MUTUAL, I_FOLLOW_ONLY, FOLLOWS_ME_ONLY, UNFOLLOWED, PENDING, CLOSE_FRIENDS, RESTRICTED, ALL
- 비교는 `Username`의 소문자 정규화 equals 사용(맞팔 집합연산). 결과는 `FollowEntry`라 팔로우 시각 보존
- 전제: `requireCompleted()`

### 4.3 `message.MessageService` — DM(3)
**책임**: 대화방·Top10·보낸/받은 비율·initiator·시간대 통계.

```java
public interface MessageService {
    MessageStats stats();        // GET /api/messages/stats
}
```
- 전제: `requireCompleted()` + **`requireOwnerResolved()`** — 미해결이면 보낸/받은·initiator 집계 보류,
  프론트가 본인 username 입력을 먼저 요구(1.4 / domain_exception 4.1)
- 본인 판별: `AccountIdentity.displayName` ↔ `sender_name` 매칭. 원문(content) 미보관

### 4.4 `activity.ActivityService` — 활동(4)
**책임**: 게시물·좋아요·댓글·저장 타임라인/월별 집계(메타데이터만).

```java
public interface ActivityService {
    long count(ActivityType type);
    List<? extends Timestamped> timeline(ActivityType type);  // 날짜순
    Map<YearMonth, Long> monthlyCounts(ActivityType type);    // 월별 막대그래프용
}
```
- `ActivityType`(신규 §5): POST, LIKE, COMMENT, SAVED (post는 post+story+reels 합산)
- 전제: `requireCompleted()`

### 4.5 `heatmap.HeatmapService` — 히트맵(5)
**책임**: 사전계산된 7×24 그리드 반환(재계산 없음).

```java
public interface HeatmapService {
    ActivityHeatmap heatmap();   // GET /api/heatmap
}
```
- 전제: **`requireCompleted()` → 미완료면 `IMPORT_NOT_COMPLETED`(503)** (G3)
- 입력 소스 5종(게시물·좋아요·댓글·DM·로그인)은 임포트 시점에 합산·캐싱됨(store가 보유)

### 4.6 `search.SearchService` — 검색(6)
```java
public interface SearchService {
    List<SearchFrequency> frequencies();   // 빈도 내림차순. GET /api/searches
}
```
- 데이터 없으면 빈 리스트 + `SEARCH_HISTORY_NOT_FOUND`(200, G4) — 에러 아님
- claim 우선순위: `logged_information/` 검색 파일은 검색이 먼저 가져감(임포트 단계에서 분류 완료)

### 4.7 `login.LoginService` — 로그인(7)
```java
public interface LoginService {
    List<LoginEvent> timeline();   // timestamp 내림차순(최신순). GET /api/logins
}
```
- 데이터 없으면 빈 리스트 + `LOGIN_HISTORY_NOT_FOUND`(200, G4)
- ip/user_agent는 문자열 그대로(지역 변환 없음)

### 4.8 `log.MiscLogService` — 각종 로그(8)
```java
public interface MiscLogService {
    List<LogFile> logs();   // 파일별 그룹. GET /api/logs
}
```
- `logged_information/` 미존재 시 빈 리스트 + `MISC_LOG_DIR_NOT_FOUND`(200, G4)
- 검색(6)이 claim하지 않은 나머지 파일만 대상

### 4.9 `overview.OverviewService` — 개요(9)
```java
public interface OverviewService {
    OverviewSummary overview();   // GET /api/overview
}
```
- **임포트 미완료여도 에러 아님** — `OverviewSummary(importRequired=true, ...)` 반환(200, domain_exception 5.1)
- 타 도메인 서비스/스토어 카운트를 조합(팔로우·활동·DM·좋아요·댓글 + 활동기간·가장 활발한 달)

### 4.10 `explorer.ExplorerService` — 탐색기(10)
```java
public interface ExplorerService {
    ExplorerNode tree();                  // GET /api/explorer/tree (미디어 제외, 깊이 10)
    RawFileContent file(String path);     // GET /api/explorer/file?path=...
}
```
- 전제/예외(G5): 루트 외부 경로 → `EXPLORER_PATH_OUT_OF_ROOT`(400) / 미존재 → `EXPLORER_FILE_NOT_FOUND`(404)
- 구조는 raw 보존, 문자열만 `StringNormalizer` 보정. 10MB 초과 시 truncate

---

## 5. 신규 보조 타입 (서비스 계층에서 필요)

| 타입 | 위치(제안) | 용도 |
|---|---|---|
| `FollowQueryType` (enum) | `domain.follow` | follows 필터 분기 (MUTUAL/I_FOLLOW_ONLY/...) |
| `ActivityType` (enum) | `domain.activity` | activity 타입 분기 (POST/LIKE/COMMENT/SAVED) |
| `ImportSnapshot` | `application.store` | 임포트 산출물 묶음(컬렉션+heatmap+owner+경고), `replaceAll` 입력 |

> 도메인 모델은 그대로 두고, 위 3개만 추가하면 서비스 시그니처가 완성된다.

## 6. 의존 관계 (요약)

```
ImportService ──writes──▶ ImportStore ◀──reads── 조회 서비스 9종
   └ FileScanner / StringNormalizer / AccountIdentityResolver
조회 서비스 ──게이트── ImportGuard (COMPLETED / ownerResolved 분기)
OverviewService ──조합── 타 도메인 카운트 (store 직접 또는 각 서비스 count())
api.Controller ──매핑── *Response DTO  ◀ Assembler ◀ 서비스 반환 도메인 모델
```

## 7. 후속 / 검증
- 이 인터페이스 확정 후: ① 신규 보조 타입 3개 추가 → ② `application` 인터페이스 파일 생성 →
  ③ 인메모리 stub 구현으로 컨트롤러 우선 개발 → ④ 실제 파서 구현 교체
- 검증: 인터페이스만으로 `compileJava` 통과 / 각 서비스에 stub 주입한 슬라이스 테스트로 게이트·예외 분기 확인
- 열린 결정거리:
  - (a) `OverviewService`가 store를 직접 읽을지 vs 각 도메인 서비스의 `count()`를 호출할지
  - (b) 반환 타입을 도메인 모델로 둘지(본안) vs 서비스가 곧장 `*Response` DTO를 반환할지
  - (c) 파싱 협력자(FileScanner 등)를 `application`에 둘지 `infrastructure` 패키지를 신설할지
```
