# 도메인별 서비스(인터페이스) 설계안 v3

> 작성: 2026-06-04 15:31 (v3 갱신) · 기준: `doc/domain.md`, `doc/domain_exception.md`, `doc/domain_io.md`, 부록 A
> 선행: 도메인 모델 47개 (`task/domain/1530_domain_log.md`)
> 이력: v1 `..._v1.md` → v2 `..._v2.md`(리뷰 A~E) → **v3(상호작용 정정 A~E)**
> v3 변경: 맨 아래 "v2 → v3 변경점" 표 참조

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

### 2.1 DTO 소유권 (B 확정, v2)
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

    boolean sourceExists(DomainType domain); // primitive 신호 (§3.3 와 계층관계 명시)
}

// 쓰기 포트 — ImportService 전용
public interface ImportWritePort {
    void replaceAll(ImportSnapshot snapshot);   // 임포트/재임포트 원자 교체

    /**
     * 수동 본인식별 fallback 적용. conversations 교체뿐 아니라 부수효과로
     *  - store 의 owner 를 주어진 owner 로 채우고
     *  - ImportResult.ownerResolved 를 true 로 전환한다.
     * (E 반영: 시그니처엔 conversations 만 보이므로 부수효과를 계약에 박아둔다)
     */
    void applyOwner(AccountIdentity owner, List<Conversation> rebuiltConversations);
}
```

### 3.2 `support.ImportGuard` — 상태 게이트 (G3 분기 단일화)
```java
public interface ImportGuard {
    void requireCompleted();      // 미완료 → IMPORT_NOT_COMPLETED(503)  [히트맵 등]
    boolean isImportRequired();   // 미완료 → true (에러 아님, 200)       [개요]
    void requireOwnerResolved();  // 미해결 → OwnerNotResolved 신호        [DM]
}
```

### 3.3 `support.Sourced<T>` — G4 "데이터 없음" 신호
```java
@Getter @AllArgsConstructor
public final class Sourced<T> {
    private final boolean sourceExists;  // false → Assembler가 *_NOT_FOUND code 부여
    private final T value;               // 없으면 빈 컬렉션 (null 아님)
}
```
**계층 관계 (D 해소)**: 보관소는 primitive 신호 `ImportReadStore.sourceExists(DomainType)` 만 제공한다.
**서비스가** 그 boolean 으로 결과를 `Sourced<T>` 로 래핑하여 API 계약화한다. 즉
`store.sourceExists()` = 내부 사실, `Sourced.sourceExists` = 외부 계약 — 같은 사실의 저수준/고수준 표현이며
중복이 아니라 변환 관계다.

---

## 4. 도메인별 서비스 인터페이스

### 4.1 `imports.ImportService` — 임포트(1) / 유일한 쓰기 진입점
```java
public interface ImportService {
    ImportResult importFrom(String folderPath);   // POST /api/import
    ImportResult status();                         // GET  /api/import/status
    ImportResult resolveOwner(String username);    // POST /api/import/owner (수동 fallback)
}
```
**전제/예외 (G1·G2)**: blank→`IMPORT_PATH_BLANK`(400) / 미존재→`IMPORT_PATH_NOT_FOUND`(400) /
비디렉토리→`IMPORT_PATH_NOT_DIRECTORY`(400) / 패턴없음→`IMPORT_NOT_INSTAGRAM_EXPORT`(422) /
HTML만→`IMPORT_HTML_ONLY`(422) / 자동 식별 실패→`COMPLETED`+`ownerResolved:false` /
`resolveOwner` blank→`OWNER_INPUT_BLANK`(400) / 단일 파일 오류는 스킵+경고(G6).

**`resolveOwner` 재파싱의 디스크 의존 (C 해소)**
- 본안(재파싱)은 owner 확정 시점에 **원본 export 폴더가 디스크에 잔존**해야 한다(메모리 절약 대가).
- import ↔ owner 확정 사이 폴더가 이동/삭제되면 message 재파싱이 실패한다.
- 처리: **재파싱 실패 시 owner 확정을 거부**하고 "원본 폴더가 없으니 재임포트하라" 안내를 반환한다
  (재임포트하면 username 을 함께 받아 owner-의존 집계까지 한 번에 구울 수 있음).
- 트레이드오프(미채택): import 시 `(sender_name, timestamp_ms)` transient 보관 → 디스크 의존 제거, 메모리 사용 증가.

**협력자는 `infrastructure`**: `FileScanner`(scan) / `StringNormalizer`(text) / `AccountIdentityResolver`(identity).
Timestamp 정규화는 `EpochMillis.normalize()` VO 가 캡슐화. `ImportSnapshot` 은 스캔된 message 파일 경로를 함께 보관.

### 4.2 `follow.FollowService` — 팔로우(2)
```java
public interface FollowService {
    FollowAnalysis analyze();                          // 맞팔(∩)·짝사랑(차집합)
    List<FollowEntry> findByType(FollowQueryType type); // GET /api/follows?type=...
}
```
- `FollowQueryType`(신규): MUTUAL, I_FOLLOW_ONLY, FOLLOWS_ME_ONLY, UNFOLLOWED, PENDING, CLOSE_FRIENDS, RESTRICTED, ALL
- 비교는 `Username` 소문자 정규화 equals. 결과는 `FollowEntry`(팔로우 시각 보존). 전제: `requireCompleted()`

### 4.3 `message.MessageService` — DM(3)  ★ owner-first
```java
public interface MessageService {
    MessageStats stats();   // GET /api/messages/stats
}
```
- owner-독립 집계(totalRooms·totalMessages·partnerName·hourlyDistribution[24])는 import 시 항상.
- owner-의존 집계(sentCount·receivedCount·ownerInitiated)는 owner 확정 시에만 채움.
- 미해결 import → owner-의존 필드 비움 + `ownerResolved:false`. 수동 `resolveOwner()` → message 재파싱 →
  `ImportWritePort.applyOwner(owner, rebuilt)` 로 교체(부수효과 §3.1).
- `stats()` 전제: `requireCompleted()` + `requireOwnerResolved()`. 미해결 시 owner-의존 수치 보류(프론트 username 유도).
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
- `monthlyCounts` 는 **타입별** 값이다. Overview 의 `mostActiveMonth`(전 활동 합산 월)는
  여기서 직접 나오지 않으며 **OverviewService 가 4타입을 머지**한다(§4.9, E 반영).

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

### 4.9 `overview.OverviewService` — 개요(9)  ★ A·B·E 핵심
```java
public interface OverviewService { OverviewSummary overview(); }   // GET /api/overview
```
**호출 순서 제약 (A 해소)** — 형제 서비스(FollowService 등)는 `requireCompleted()` 로 게이트되어 미완료 시 503.
Overview 는 미완료여도 200(importRequired) 여야 하므로:
1. `overview()` 는 **맨 먼저** `guard.isImportRequired()` 를 확인하고, true 면 즉시
   `OverviewSummary(importRequired=true, ...)` 로 **early-return** 한다.
2. 이 분기 **이전엔 게이트 걸린 형제 서비스를 절대 호출하지 않는다** (503 누수 방지).

**집계 소스 정밀화 (B 해소)** — 결정 (a)의 "형제 서비스 재사용"은 게이트 호환되는 것에 한정한다:
- `mutualCount` 등 팔로우 수치 → `FollowService.analyze()` **재사용**(맞팔 계산 단일화).
- DM 카드(대화방 수·총 메시지)는 owner-독립이지만 `MessageService.stats()` 는 `requireOwnerResolved()`
  게이트라 **재사용 불가** → `ImportReadStore.conversations()` 에서 **직접** 읽어 카운트한다(stats() 우회).
- `mostActiveMonth` → `ActivityService.monthlyCounts()` 4타입(POST/LIKE/COMMENT/SAVED)을
  **OverviewService 가 머지**해 월별 합산 최댓값을 구한다(E 반영).

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

## 6. 의존 관계 (v3)

```
ImportService ──writes──▶ ImportWritePort ─┐
   │ (infrastructure 협력자)                ├─▶ [인메모리 보관소]
   └ FileScanner·StringNormalizer·          │
     AccountIdentityResolver                │
조회 서비스 ──reads──▶ ImportReadStore ◀────┘   (쓰기 메서드 안 보임)
조회 서비스 ──게이트── ImportGuard

OverviewService:
   1) guard.isImportRequired()? ─true─▶ importRequired 응답 (형제 호출 전에 early-return)
   2) false ─▶ FollowService.analyze() 재사용  +  ImportReadStore.conversations() 직접(DM, stats() 우회)
              +  ActivityService.monthlyCounts() 4타입 머지(mostActiveMonth)

resolveOwner ─재파싱(message 경로)─▶ applyOwner(owner, rebuilt)  [owner·ownerResolved 부수효과]
              └─ 폴더 잔존 안 함 ─▶ owner 확정 거부 + 재임포트 안내

api.Controller ──Assembler──▶ domain.x.dto  (store.sourceExists → Sourced<T> → code 부여)
```

## 7. v2 → v3 변경점

| # | 지적 | 반영 |
|---|---|---|
| A | Overview가 게이트 걸린 형제 호출 시 503 누수 | `overview()` 맨 앞 `isImportRequired()` early-return, 그 전 형제 호출 금지 (§4.9) |
| B | Overview DM 카드를 게이트된 stats()로 못 가져옴 | (a) 정밀화: FollowService.analyze()만 재사용, DM은 `ImportReadStore.conversations()` 직접 (§4.9) |
| C | resolveOwner 재파싱의 디스크 잔존 의존 | 재파싱 실패 시 owner 확정 거부 + 재임포트 안내 명시 (§4.1) |
| D | sourceExists 가 store/Sourced 두 곳 중복 표현 | "store=primitive 사실 → 서비스가 Sourced로 래핑(계약)" 변환 관계 명시 (§3.3) |
| E | applyOwner 부수효과·mostActiveMonth 책임 모호 | applyOwner javadoc에 owner/ownerResolved 부수효과 박음(§3.1), mostActiveMonth는 Overview가 4타입 머지(§4.4·§4.9) |

## 8. 후속 / 검증
- 확정 후: ① 보조 타입 4개 추가 → ② `application`/`infrastructure` 인터페이스 생성 →
  ③ 인메모리 stub 으로 컨트롤러 우선 개발 → ④ 실제 파서 구현 교체
- 검증: 인터페이스만으로 `compileJava` 통과 / stub 슬라이스 테스트로
  ▸ 미완료 시 overview 200(형제 미호출) ▸ owner 미해결 시 overview DM 카운트 정상(stats 우회)
  ▸ 히트맵 503 ▸ G4 sourceExists ▸ resolveOwner 재파싱 실패 분기 확인
