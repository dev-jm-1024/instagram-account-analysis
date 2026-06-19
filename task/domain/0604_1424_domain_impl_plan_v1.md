# Instagram Analyzer — 도메인 자바 클래스 구현 플랜 (v1)

> 작성일: 2026-06-04 14:24 · 기준 문서: `doc/domain.md` (Domain V1.0), `doc/domain_exception.md`, `doc/domain_io.md`
> 리뷰 반영: 2026-06-04 (아래 "리뷰 반영 변경점" 참조)

## Context

`doc/domain.md`에 정의된 10개 도메인을 자바 도메인 모델로 구현한다.
현재 백엔드는 스켈레톤 상태(`InstagramAnalyzeApplication` 1개 + 빈 `domain/` 패키지)이며,
도메인 모델이 없어 이후 Service/Controller 작업을 시작할 수 없다.

전제(domain.md 기준):
- **포터블 단일 사용자 / localhost / DB 없음** → 인증·세션·멀티유저 개념 없음, 메모리(@Service) 적재
- **전 데이터 읽기 전용** → 도메인 객체는 불변(`final` 필드 + 불변 컬렉션/방어복사)
- **공통 파싱 원칙** → timestamp 전부 epoch ms 통일, 문자열 mojibake 보정, 미디어 스킵

## 작성 규칙 (합의 사항)

- **POJO 방식**, Lombok `@Getter` / `@AllArgsConstructor` 사용 허용
- **VO로 뺄 수 있는 것은 VO로 분리** (`EpochMillis`, `Username`, `AccountIdentity` 등)
- **공통 동작은 인터페이스(default 메서드)로 추상화** 후 구현 (← 리뷰 반영: 추상 클래스 B안 폐기)
  - `Timestamped` 인터페이스가 `EpochMillis getTimestamp()`만 요구하고 요일/시 변환은 default로 제공.
    필드 중복 선언·생성자 우회 트릭이 사라지고, 단일 상속 슬롯을 태우지 않음.
  - 자식은 `private final EpochMillis timestamp;` + `@Getter @AllArgsConstructor`만으로 `getTimestamp()` 충족.
- **불변성 강제**: 컬렉션/배열을 가진 클래스는 `@AllArgsConstructor` 대신 **방어복사 생성자**를 쓰고
  getter는 불변 뷰(`List.copyOf` / 배열 복사본)를 반환한다 (스칼라 전용 클래스만 `@AllArgsConstructor`).
- 생성 범위는 **전체** (원시 엔티티 + 집계/결과 모델 + 임포트 메타 + Explorer + VO/Enum)

도메인 클래스는 순수 모델만 만든다. JSON 파싱용 DTO·Service·Controller·예외 클래스는
이번 범위가 아니다(예외 코드는 `ParseWarningCode` enum으로만 반영).

```java
// 인터페이스 패턴 (리뷰 B 반영)
public interface Timestamped {
    EpochMillis getTimestamp();
    default int dayOfWeekIndex(ZoneId z) { return getTimestamp().dayOfWeekIndex(z); } // 0=월
    default int hourOfDay(ZoneId z)      { return getTimestamp().hourOfDay(z); }      // 0~23
}

@Getter
@AllArgsConstructor
public class Post implements Timestamped {
    private final EpochMillis timestamp; // getter가 getTimestamp() 충족
    private final PostType type;
    private final String title;
}
```

---

## 산출물: 자바 도메인 클래스

베이스 패키지 `com.instagram.analyze.domain`, 도메인별 하위 패키지로 구성
(`import`은 자바 예약어라 패키지명으로 못 써서 `imports` 사용).

### `domain.common` — 공통 추상화 / VO / 공유 Enum
| 파일 | 종류 | 핵심 |
|---|---|---|
| `Timestamped` | **인터페이스** | `EpochMillis getTimestamp()` + default `dayOfWeekIndex/hourOfDay/toLocalDateTime(ZoneId)` (부록 C 로컬타임 정책) |
| `EpochMillis` | **VO** | `long value`; `normalize(long raw)`(`<10_000_000_000L` → ×1000, 1.3절) → `of(long)`; `of()`는 0 이하 거부(fail-fast). `dayOfWeekIndex(ZoneId)`(**0=월** 단일 변환 지점, D 해소), `hourOfDay`, `toLocalDateTime`. equals/hashCode by value |
| `Username` | **VO** | `String value`; equals/hashCode는 `toLowerCase(Locale.ROOT)` 기준 (맞팔 집합연산용, 2절 대소문자 무시) |
| `AccountIdentity` | **VO** | `Username username` + `String displayName` (본인식별 2형태, 1.4절) |
| `ParseWarning` | **VO** | `ParseWarningCode code` + `String source` + `String detail` (G6 누적 경고) |
| `ParseWarningCode` | enum | SCHEMA_MISMATCH, TIMESTAMP_INVALID, VALUE_BLANK, STRING_LIST_EMPTY, JSON_ERROR, FILE_EMPTY, STRING_DECODE_FAILED |
| `DomainType` | enum | IMPORT, FOLLOW, MESSAGE, ACTIVITY, HEATMAP, SEARCH, LOGIN, MISC_LOG, OVERVIEW, EXPLORER, UNKNOWN (0.2 도메인 지도) |
| `MediaType` | enum | JPG, MP4, PNG, MOV, WEBP + `isMedia(String filename)` (스캐너/탐색기 제외용) |

### `domain.imports` — 임포트(1)
- `ImportStatus` (enum): IDLE, IN_PROGRESS, COMPLETED, FAILED
- `ImportResult`: `status`, `AccountIdentity owner`, `boolean ownerResolved`, `EpochMillis completedAt`,
  `long durationMillis`, `int parsedItemCount`, `List<ParseWarning> warnings`(불변) (1절 메타데이터)

### `domain.follow` — 팔로우(2)
- `FollowRelationType` (enum): FOLLOWING, FOLLOWER, RECENTLY_UNFOLLOWED, PENDING_REQUEST, CLOSE_FRIEND, RESTRICTED
- `FollowEntry implements Timestamped`: `EpochMillis timestamp`(팔로우 시각) + `Username username` + `FollowRelationType relationType`
- `FollowAnalysis` (집계): `followerCount`, `followingCount`, `List<FollowEntry> mutual`(∩),
  `iFollowOnly`(내가 짝사랑, following−followers), `followsMeOnly`(나를 짝사랑, followers−following)
  - **리뷰 E 반영**: 결과를 `List<Username>`이 아닌 `List<FollowEntry>`로 보유 → `followedAt` 보존
    (domain_io 4절 "username + 팔로우 시각" OUT 스펙 충족, 시각순 정렬 가능). 모두 불변 리스트.

### `domain.message` — DM(3)  (원문 미보관, 통계만)
- `Conversation`: `roomId`, `partnerName`, `totalCount`, `sentCount`, `receivedCount`, `boolean ownerInitiated`, `int[] hourlyDistribution`(24, 방어복사)
- `MessageStats` (집계): `totalRooms`, `long totalMessages`, `List<Conversation> topPartners`(Top10, 불변), `int[] hourlyDistribution`(24, 방어복사)
- ※ DM은 요일축이 없는 24버킷만 보유 → 히트맵 요일 복원 불가. **히트맵 산출 전제는 아래 heatmap 항목 참조(리뷰 A).**

### `domain.activity` — 활동(4)  (Timestamped 집중 활용)
- `PostType` (enum): POST, STORY, REELS
- `LikeTargetType` (enum): POST, COMMENT
- `CommentSource` (enum): POST, REELS
- `Post implements Timestamped`: `EpochMillis timestamp`(creation) + `PostType type` + `String title`(캡션)
- `LikeEntry implements Timestamped`: `timestamp` + `LikeTargetType target` + `String href`
- `CommentEntry implements Timestamped`: `timestamp` + `CommentSource source` + `String content`
- `SavedPost implements Timestamped`: `timestamp` + `String href`

### `domain.heatmap` — 히트맵(5)
- `ActivityHeatmap`: `int[][] grid`(7×24, 방어복사) + `HeatmapPeak peak`
- `HeatmapPeak` (VO): `int dayOfWeek`(**0=월**, `EpochMillis.dayOfWeekIndex`와 동일 기준) + `int hour`(0~23) + `int count`
- **리뷰 A 반영 — 산출 전제 명시**: `ActivityHeatmap`은 *도메인 모델(MessageStats 등)의 사후 조합이 아니라*,
  **임포트 시점에 메모리에 살아있는 transient timestamp 스트림(게시물·좋아요·댓글·DM 메시지·로그인 5종)으로부터
  1회 사전계산되어 캐싱되는 결과**다 (domain.md 5절). DM 메시지 timestamp는 통계로 뭉개지기 전에
  히트맵 집계에 직접 투입해야 한다. 즉 히트맵 집계 시점 = 임포트 ETL 중(메시지 ts 보존 구간).
  도메인 모델 `ActivityHeatmap`은 그 계산 결과를 담는 그릇일 뿐, 스스로 재구성 책임을 갖지 않는다.

### `domain.search` — 검색(6)
- `SearchEntry implements Timestamped`: `timestamp` + `String keyword`
- `SearchFrequency` (VO): `String keyword` + `long count` (빈도 내림차순용)

### `domain.login` — 로그인(7)
- `LoginEventType` (enum): LOGIN, LOGOUT
- `LoginEvent implements Timestamped`: `timestamp` + `LoginEventType type` + `String ipAddress` + `String userAgent`

### `domain.log` — 각종 로그(8)  (고정 스키마 없음)
- `LogRecord`: `Map<String,String> fields`(불변) + `EpochMillis timestamp`(nullable; `Timestamped` 미구현)
- `LogFile`: `String fileName` + `List<LogRecord> records`(불변)

### `domain.overview` — 개요(9)
- `OverviewSummary`: 팔로워/팔로잉/맞팔/짝사랑 수, 총게시물(post+story+reels), DM방·메시지 수,
  좋아요·댓글 수, `EpochMillis activityFrom/activityTo`(활동기간), `String mostActiveMonth`, `boolean importRequired`

### `domain.explorer` — 탐색기(10)
- `ExplorerNode`: `String name` + `String relativePath` + `boolean directory` + `List<ExplorerNode> children`(불변)
- `RawFileContent`: `String path` + `String content`(정규화 JSON) + `boolean truncated`(10MB 초과)

> 총 약 35개 파일.

---

## 설계 노트
- **공통 추상화 = 인터페이스**: `Timestamped`는 필드 없이 `getTimestamp()` + default 동작만 제공.
  히트맵/개요가 도메인 무관하게 timestamp를 동일 인터페이스로 다룬다. 단일 상속 슬롯을 보존해
  추후 다른 base가 필요해도 충돌 없음.
- **요일 인덱스 단일 변환 지점(D)**: `java.time.DayOfWeek`(MON=1…SUN=7)와 그리드 인덱스(0=월)의
  오프바이원을 막기 위해 변환을 `EpochMillis.dayOfWeekIndex(ZoneId)` 한 곳에만 둔다.
  히트맵 grid 인덱스·`HeatmapPeak.dayOfWeek`는 모두 이 0-based 값을 사용.
- **불변성(C)**: VO/엔티티 전부 `final` 필드. 컬렉션은 생성자에서 `List.copyOf`, 배열은 `clone()`으로
  방어복사하고 getter도 복사본/불변뷰 반환. 이런 클래스는 `@AllArgsConstructor` 대신 수동 생성자 사용.
- **검증은 경계에(F)**: 도메인은 "정규화·검증 완료된 값만" 보유. `EpochMillis.of()`가 0 이하를
  fail-fast로 거부하므로 도메인 내부엔 항상 유효한 timestamp만 존재. `isValid()` 같은 의심 신호는 두지 않음.
  null/0 이하 timestamp 필터링은 Service 파싱 단계에서 `PARSE_TIMESTAMP_INVALID` 경고로 처리.
- **VO 불변성 구현**: VO는 `@Getter` + `@EqualsAndHashCode` + `private` 생성자 + `static of(...)` 팩토리.
  `Username` equals가 2절 대소문자 무시를, `EpochMillis`가 1.3 정규화·부록 C 시간변환을 캡슐화.
- **파싱/DTO 비포함**: Jackson 스트리밍 DTO, mojibake 디코더, 스캐너 구현은 Service 단계 작업이라 제외.

---

## 리뷰 반영 변경점 (v1 초안 → 현행)
| # | 지적 | 반영 |
|---|---|---|
| A | DM이 24버킷이라 히트맵 요일축 복원 불가 | 히트맵을 "임포트 시점 transient ts 5종 사전계산 결과"로 명시. `ActivityHeatmap`은 결과 그릇 |
| B | 추상 클래스 대신 인터페이스가 깔끔 | `TimestampedRecord`(추상클래스) → `Timestamped`(인터페이스 + default) |
| C | 배열/컬렉션이 불변성 뚫음 | 컬렉션/배열 보유 클래스는 방어복사 생성자 + 불변 getter (해당 클래스는 `@AllArgsConstructor` 미사용) |
| D | 0=월 vs DayOfWeek(MON=1) 오프바이원 | `EpochMillis.dayOfWeekIndex(ZoneId)` 단일 변환 지점 |
| E | FollowAnalysis가 팔로우 시각 버림 | 결과를 `List<FollowEntry>`로 (followedAt 보존) |
| F | isValid()가 모순 신호 | 제거. `of()` fail-fast, 검증은 Service 경계 |

---

## 검증
- `./gradlew compileJava` 로 전체 컴파일 통과 확인 (Java 21, Lombok 적용 여부 포함)
- 인터페이스 다형성 검증: `Timestamped ref = new Post(...)` / `heatmap 집계가 List<Timestamped>를 받아
  dayOfWeekIndex·hourOfDay로 7×24 누적` 가능한지 컴파일 레벨 확인
- 불변성 스모크: getter로 받은 `List`/배열 수정 시도가 막히는지(불변뷰/복사본) 간단 확인
- 별도 런타임/테스트는 도메인 모델만으로는 불필요 — Service 단계에서 단위테스트 작성 예정
