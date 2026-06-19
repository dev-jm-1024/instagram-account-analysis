# 도메인별 서비스(인터페이스) 설계안 v2

> 작성: 2026-06-04 15:31 (v2 갱신) · 기준: `doc/domain.md`, `doc/domain_exception.md`, `doc/domain_io.md`, 부록 A
> 선행: 도메인 모델 47개 (`task/domain/1530_domain_log.md`) · v1: `0604_1531_interface_plan_v1.md`
> v2 변경: 리뷰 A~E + 열린결정 3개 반영 (맨 아래 "리뷰 반영 변경점" 표 참조)

## 1. Context / 전제

도메인 모델 위에 **조회용 서비스 계층**을 인터페이스로 정의한다. 구현(@Service)은 다음 단계.

- **DB 없음 / 인메모리** — 파싱 결과는 단일 보관소에 적재, Java Stream 집계
- **읽기 전용** — 유일한 쓰기는 임포트(ETL) + 수동 본인식별(fallback). 나머지는 조회만
- **단일 사용자 / localhost** — 세션·인증·동시성 제어 없음
- **상태 게이트** — 임포트 `COMPLETED`, 본인식별 `ownerResolved` 가 일부 서비스의 전제

## 2. 계층 / 패키지 구조 (v2)

```
com.instagram.analyze
├── domain/...                  엔티티·VO·DTO  (DTO '정의'는 domain.x.dto 에 유지)
├── application/                서비스 인터페이스 = 순수 오케스트레이션
│   ├── store/                  ImportReadStore(읽기 포트) · ImportWritePort(쓰기 포트)
│   ├── support/                ImportGuard · Sourced<T>
│   ├── imports/  ImportService
│   ├── follow/ message/ activity/ heatmap/ search/ login/ log/ overview/ explorer/
├── infrastructure/             파싱·IO 협력자 (인프라)   ← 열린결정 (c) 반영
│   ├── scan/      FileScanner
│   ├── text/      StringNormalizer
│   ├── identity/  AccountIdentityResolver
│   └── parse/     도메인별 파서 (후속)
└── api/                        Controller + Assembler
```

### 2.1 DTO 소유권 정리 (리뷰 B 해소)
- DTO **정의**는 이전 구조 결정대로 `domain.x.dto` 에 둔다 (위치 변경 없음).
- DTO를 **생성**하는 유일한 지점은 `api.Assembler` 다. 도메인/서비스는 DTO를 만들거나 반환하지 않는다.
- `api` 계층에 별도 DTO를 만들지 않는다 → **이중 소유 금지**. (v1의 "DTO=api 계층" 전제 폐기)

### 2.2 반환 타입 규약 (열린결정 b 확정)
- 서비스는 **도메인 모델/집계**(`FollowAnalysis`, `MessageStats`, `ActivityHeatmap`, `List<LoginEvent>`…)를 반환한다.
- `*Response` DTO 변환은 `api.Assembler` 가 도메인 모델 → `domain.x.dto` 로 채운다.

## 3. 공통 횡단 관심사

### 3.1 보관소 — 읽기/쓰기 포트 분리 (리뷰 C 해소, ISP)

```java
// 읽기 포트 — 조회 서비스 9종이 의존 (쓰기 메서드를 볼 수 없음)
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
    ActivityHeatmap heatmap();            // 임포트 시점 사전계산본 (owner 무관, §4.5)

    boolean sourceExists(DomainType domain); // G4 신호: "빈 결과" ≠ "소스 파일 없음" (리뷰 D)
}

// 쓰기 포트 — ImportService 전용. 두 개의 쓰기 경로를 명시적으로 격리.
public interface ImportWritePort {
    void replaceAll(ImportSnapshot snapshot);                       // 임포트/재임포트 원자 교체
    void applyOwner(AccountIdentity owner, List<Conversation> rebuiltConversations); // 수동 fallback
}
```
- `replaceAll` 외의 두 번째 쓰기였던 `updateOwner` 를 **쓰기 포트로 격리**하고, 단순 owner 교체가
  아니라 **DM 재집계 결과까지 함께 받는** `applyOwner` 로 바꿔 A 이슈의 재계산 책임을 못박는다.

### 3.2 `support.ImportGuard` — 상태 게이트 (G3 분기 단일화)
```java
public interface ImportGuard {
    void requireCompleted();      // 미완료 → IMPORT_NOT_COMPLETED(503)  [히트맵 등]
    boolean isImportRequired();   // 미완료 → true (에러 아님, 200)       [개요]
    void requireOwnerResolved();  // 미해결 → OwnerNotResolved 신호        [DM]
}
```

### 3.3 `support.Sourced<T>` — G4 "데이터 없음" 신호 (리뷰 D 해소)
빈 리스트만으로 구분 못 하는 "파일 없음 vs 항목 0" 을 서비스 계약에 싣는다.
```java
@Getter @AllArgsConstructor
public final class Sourced<T> {
    private final boolean sourceExists;  // false → Assembler가 *_NOT_FOUND code 부여
    private final T value;               // 항상 비어있지 않은 컬렉션(없으면 빈 컬렉션)
}
```
> Assembler 규칙: `sourceExists == false` → 해당 code(`SEARCH_HISTORY_NOT_FOUND` 등) + 빈 배열(200).

---

## 4. 도메인별 서비스 인터페이스

### 4.1 `imports.ImportService` — 임포트(1) / 유일한 쓰기 진입점
**책임**: 경로 검증 → 스캔 → 스트리밍 파싱·정규화 → 본인식별 → 적재 → 히트맵 사전계산.

```java
public interface ImportService {
    ImportResult importFrom(String folderPath);   // POST /api/import
    ImportResult status();                         // GET  /api/import/status
    ImportResult resolveOwner(String username);    // POST /api/import/owner (수동 fallback)
}
```
**전제/예외 (G1·G2)**
- blank → `IMPORT_PATH_BLANK`(400) / 미존재 → `IMPORT_PATH_NOT_FOUND`(400) / 비디렉토리 → `IMPORT_PATH_NOT_DIRECTORY`(400)
- export 패턴 없음 → `IMPORT_NOT_INSTAGRAM_EXPORT`(422) / HTML만 → `IMPORT_HTML_ONLY`(422)
- 자동 본인식별 실패 → `COMPLETED` + `ownerResolved:false` (에러 아님)
- `resolveOwner` username blank → `OWNER_INPUT_BLANK`(400)
- 단일 파일 오류는 스킵 + 경고 누적(G6)

**협력자는 `infrastructure` 로 분리** (열린결정 c)
```java
public interface FileScanner {            // infrastructure.scan — 파일시스템 IO
    Map<DomainType, List<Path>> scan(Path root);   // glob 매칭, 미디어/0바이트 제외
}
public interface StringNormalizer {       // infrastructure.text — 인코딩 IO
    String normalize(String raw);                  // ISO-8859-1 → UTF-8 (mojibake)
}
public interface AccountIdentityResolver { // infrastructure.identity
    Optional<AccountIdentity> resolve(Map<DomainType, List<Path>> scanned);
}
```
> Timestamp 정규화(1.3)는 `EpochMillis.normalize()` VO가 캡슐화 → 인터페이스 불필요.
> `ImportSnapshot` 은 **스캔된 message 파일 경로를 함께 보관**한다(§4.3 재집계용).

---

### 4.2 `follow.FollowService` — 팔로우(2)
```java
public interface FollowService {
    FollowAnalysis analyze();                          // 맞팔(∩)·짝사랑(차집합)
    List<FollowEntry> findByType(FollowQueryType type); // GET /api/follows?type=...
}
```
- `FollowQueryType`(신규): MUTUAL, I_FOLLOW_ONLY, FOLLOWS_ME_ONLY, UNFOLLOWED, PENDING, CLOSE_FRIENDS, RESTRICTED, ALL
- 비교는 `Username` 소문자 정규화 equals. 결과는 `FollowEntry`라 팔로우 시각 보존
- 전제: `requireCompleted()`

### 4.3 `message.MessageService` — DM(3)  ★ 리뷰 A 핵심
```java
public interface MessageService {
    MessageStats stats();   // GET /api/messages/stats
}
```
**owner-first 정책 (A 해소)** — sent/received/initiator 는 `displayName ↔ sender_name` 매칭이 필요하고,
content 는 미보관이라 owner 없이 구운 카운트는 소급 보정이 불가능하다. 따라서:

1. **owner-독립 집계**(totalRooms, totalMessages, partnerName, hourlyDistribution[24])는 import 시 항상 계산.
2. **owner-의존 집계**(sentCount/receivedCount/ownerInitiated)는 owner 확정 시에만 채운다.
   - import 시 owner 해결됨 → Conversation 전체를 구워 store 적재.
   - import 시 owner 미해결 → owner-의존 필드는 비워두고(0/false) `ownerResolved:false` 유지.
3. **수동 `resolveOwner()`** → ImportService 가 **store에 보관된 message 파일 경로를 재파싱**하여
   Conversation 을 owner-의존 필드까지 다시 빌드 → `ImportWritePort.applyOwner(owner, rebuilt)` 로 교체.
4. `stats()` 전제: `requireCompleted()` + `requireOwnerResolved()`. 미해결이면 owner-의존 수치는 보류
   상태로 응답(프론트가 username 입력 유도, domain_exception 4.1).

> 대안(메모리↔CPU 트레이드오프, 미채택): import 시 메시지별 `(sender_name, timestamp_ms)` 만
> transient 보관(content 제외)하면 재파싱 없이 재집계 가능. 본안은 재파싱(메모리 절약) 채택.
> 히트맵의 DM 기여분은 timestamp 만 쓰므로 **owner 와 무관하게** import 시 항상 산출된다(§4.5).

### 4.4 `activity.ActivityService` — 활동(4)  (리뷰 E: 와일드카드 제거)
```java
public interface ActivityService {
    long count(ActivityType type);                 // post=post+story+reels 합산
    Map<YearMonth, Long> monthlyCounts(ActivityType type);  // 월별 스택 막대용
    List<Post> posts();
    List<LikeEntry> likes();
    List<CommentEntry> comments();
    List<SavedPost> savedPosts();
}
```
- `ActivityType`(신규): POST, LIKE, COMMENT, SAVED
- 타임라인은 `List<? extends Timestamped>` 대신 **타입별 구체 반환** → Assembler가 타입별 DTO 매핑 용이
- 전제: `requireCompleted()`

### 4.5 `heatmap.HeatmapService` — 히트맵(5)
```java
public interface HeatmapService {
    ActivityHeatmap heatmap();   // GET /api/heatmap
}
```
- 전제: `requireCompleted()` → 미완료면 `IMPORT_NOT_COMPLETED`(503) (G3)
- 입력 5종(게시물·좋아요·댓글·DM·로그인)은 import 시 timestamp 만으로 합산·캐싱.
  **DM 기여분은 owner 무관** → A 이슈와 충돌하지 않음(재계산 불필요).

### 4.6 `search.SearchService` — 검색(6)
```java
public interface SearchService {
    Sourced<List<SearchFrequency>> frequencies();   // 빈도 내림차순. GET /api/searches
}
```
- `sourceExists=false` → `SEARCH_HISTORY_NOT_FOUND`(200, G4)
- claim 우선순위: `logged_information/` 검색 파일은 검색이 먼저 가져감(import 단계 분류 완료)

### 4.7 `login.LoginService` — 로그인(7)
```java
public interface LoginService {
    Sourced<List<LoginEvent>> timeline();   // 최신순. GET /api/logins
}
```
- `sourceExists=false` → `LOGIN_HISTORY_NOT_FOUND`(200, G4). ip/user_agent 문자열 그대로

### 4.8 `log.MiscLogService` — 각종 로그(8)
```java
public interface MiscLogService {
    Sourced<List<LogFile>> logs();   // 파일별 그룹. GET /api/logs
}
```
- `sourceExists=false`(= `logged_information/` 미존재) → `MISC_LOG_DIR_NOT_FOUND`(200, G4)
- 검색(6)이 claim하지 않은 나머지 파일만 대상

### 4.9 `overview.OverviewService` — 개요(9)  (열린결정 a: 서비스 호출)
```java
public interface OverviewService {
    OverviewSummary overview();   // GET /api/overview
}
```
- 미완료여도 에러 아님 → `OverviewSummary(importRequired=true, ...)` (200, domain_exception 5.1)
- **다른 도메인 서비스를 호출해 조합**한다. 특히 `mutualCount` 는 `FollowService.analyze()`(교집합 로직)
  를 재사용 → 맞팔 계산이 두 군데로 갈라지지 않음(집계 단일화).
- DM 관련 카드(대화방 수·총 메시지 수)는 owner-독립 값이라 owner 미해결이어도 표시 가능.

### 4.10 `explorer.ExplorerService` — 탐색기(10)
```java
public interface ExplorerService {
    ExplorerNode tree();                  // GET /api/explorer/tree (미디어 제외, 깊이 10)
    RawFileContent file(String path);     // GET /api/explorer/file?path=...
}
```
- 전제/예외(G5): 루트 외부 → `EXPLORER_PATH_OUT_OF_ROOT`(400) / 미존재 → `EXPLORER_FILE_NOT_FOUND`(404)
- 구조 raw 보존, 문자열만 `StringNormalizer` 보정. 10MB 초과 시 truncate

---

## 5. 신규 보조 타입

| 타입 | 위치 | 용도 |
|---|---|---|
| `FollowQueryType` (enum) | `domain.follow` | follows 필터 분기 |
| `ActivityType` (enum) | `domain.activity` | activity 타입 분기 |
| `ImportSnapshot` | `application.store` | 임포트 산출물 묶음(컬렉션+heatmap+owner+경고**+스캔 message 경로**) |
| `Sourced<T>` | `application.support` | G4 "소스 존재 여부" 신호 래퍼 |

## 6. 의존 관계 (v2)

```
ImportService ──writes──▶ ImportWritePort ─┐
   │ (협력자, infrastructure)               ├─▶ [인메모리 보관소]
   └ FileScanner·StringNormalizer·          │
     AccountIdentityResolver                │
조회 서비스 9종 ──reads──▶ ImportReadStore ◀┘   (쓰기 메서드 안 보임)
조회 서비스 ──게이트── ImportGuard
OverviewService ──호출── FollowService/ActivityService/MessageService (집계 재사용)
api.Controller ──Assembler──▶ domain.x.dto  (DTO 생성 유일 지점)
resolveOwner ──재파싱(message 경로)──▶ applyOwner(owner, rebuiltConversations)
```

## 7. 리뷰 반영 변경점 (v1 → v2)

| # | 지적 | 반영 |
|---|---|---|
| A | 사후 owner 확정 시 DM 재계산 경로 없음 (Conversation final 카운트 모순) | owner-first 정책: owner-독립/의존 집계 분리 + `resolveOwner`가 message 재파싱→`applyOwner`로 재집계. 히트맵 DM은 owner 무관 명시 |
| B | DTO 위치 충돌 (domain.x.dto vs "api 계층") | DTO 정의는 domain.x.dto 유지, 생성은 api.Assembler 단일 지점, api 중복 DTO 금지 |
| C | ImportStore 과대(ISP) | 읽기 포트 / 쓰기 포트 분리, updateOwner→applyOwner로 쓰기 포트에 격리 |
| D | G4 "데이터 없음" 신호 부재 | `Sourced<T>`(sourceExists) 래퍼로 Search/Login/MiscLog 반환 |
| E | `List<? extends Timestamped>` 와일드카드 | 타입별 구체 메서드(posts/likes/comments/savedPosts) + monthlyCounts |
| (a) | Overview: store 직접 vs 서비스 | 서비스 호출(맞팔 계산 단일화) |
| (b) | 반환: 도메인 모델 vs DTO | 도메인 모델 유지 (B와 함께 일관) |
| (c) | 협력자: application vs infrastructure | `infrastructure` 패키지 신설 |

## 8. 후속 / 검증
- 확정 후: ① 보조 타입 4개 추가 → ② `application`/`infrastructure` 인터페이스 파일 생성 →
  ③ 인메모리 stub 구현으로 컨트롤러 우선 개발 → ④ 실제 파서 구현 교체
- 검증: 인터페이스만으로 `compileJava` 통과 / stub 주입 슬라이스 테스트로
  게이트(503·importRequired)·G4(sourceExists)·owner-재집계 분기 확인
