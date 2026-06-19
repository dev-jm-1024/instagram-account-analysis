# 도메인 자바 클래스 구현 작업 로그

> 작성: 2026-06-04 15:30 · 기준 문서: `doc/domain.md`, `doc/domain_exception.md`, `doc/domain_io.md`
> 계획 문서: `task/domain/0604_1424_domain_impl_plan_v1.md`

## 1. 작업 개요

`doc/domain.md`(Domain V1.0)의 10개 도메인을 자바 도메인 모델로 구현했다.
백엔드 스켈레톤(빈 `domain/` 패키지)에 도메인 모델 계층을 채워 이후 Service/Controller 작업의 기반을 마련.

- 베이스 패키지: `com.instagram.analyze.domain`
- 환경: Java 21, Spring Boot 4.0.6, Lombok, spring-boot-starter-validation
- 생성 파일: **47개** (컴파일 시 중첩 static DTO 7개 포함 54 class)
- 컴파일 검증: `./gradlew compileJava` 통과 (에러 없음)

## 2. 설계 규칙 (사용자 합의)

| 항목 | 결정 |
|---|---|
| 스타일 | POJO + Lombok `@Getter` / `@AllArgsConstructor` |
| 공통 추상화 | 추상 클래스 대신 **`Timestamped` 인터페이스 + default 메서드** (리뷰 B) |
| VO 정책 | **식별자·정규화 필요한 것만** VO 분리 (필드 단위 세분 VO는 미적용) |
| DTO 정책 | **조회 응답 DTO 중심** + 실제 요청 DTO 2개(ImportRequest, OwnerRequest) |
| 패키지 구조 | 도메인별 패키지 + 내부 `vo/`·`dto/` 분리, 엔티티는 패키지 루트 |
| 불변성 | 컬렉션/배열 보유 클래스는 `@AllArgsConstructor` 대신 방어복사 생성자 + 불변 getter |

## 3. 생성 파일 목록 (패키지별)

### common (8) — cross-cutting 공통
- `Timestamped` (interface): `getTimestamp()` + default `toLocalDateTime/dayOfWeekIndex/dayOfWeek/hourOfDay(ZoneId)`
- `DomainType` (enum): 0.2 도메인 지도 11종 + UNKNOWN
- `MediaType` (enum): JPG/MP4/PNG/MOV/WEBP + `isMedia(filename)`
- `ParseWarningCode` (enum): G6 내부 경고 7종
- `vo/EpochMillis`: epoch ms VO, `normalize()`(<10_000_000_000L→×1000), `of()` fail-fast, `dayOfWeekIndex()`(0=월)
- `vo/Username`: 원본 보존 + 소문자 정규화 기준 equals/hashCode (맞팔 집합연산)
- `vo/AccountIdentity`: username + displayName (본인식별 2형태)
- `vo/ParseWarning`: code + source + detail

### imports (5) — 임포트(1)
- `ImportStatus` (enum): IDLE/IN_PROGRESS/COMPLETED/FAILED
- `ImportResult`: 상태·owner·ownerResolved·완료시각·소요시간·항목수·경고(불변)
- `dto/ImportRequest` *: `@NotBlank folderPath`
- `dto/OwnerRequest` *: `@NotBlank username`
- `dto/ImportStatusResponse`

### follow (4) — 팔로우(2)
- `FollowRelationType` (enum): 6종 루트키
- `FollowEntry implements Timestamped`: timestamp + Username + relationType
- `FollowAnalysis`: mutual/iFollowOnly/followsMeOnly = **`List<FollowEntry>`**(팔로우 시각 보존, 리뷰 E)
- `dto/FollowResponse` (+ nested FollowItem)

### message (3) — DM(3)
- `Conversation`: roomId·partnerName·sent/received·ownerInitiated·hourlyDistribution[24](방어복사). 원문 미보관
- `MessageStats`: totalRooms·totalMessages·topPartners(Top10)·hourlyDistribution[24]
- `dto/MessageStatsResponse` (+ nested PartnerStat)

### activity (8) — 활동(4)
- enum: `PostType`(POST/STORY/REELS), `LikeTargetType`(POST/COMMENT), `CommentSource`(POST/REELS)
- `Post`, `LikeEntry`, `CommentEntry`, `SavedPost` — 모두 `implements Timestamped`
- `dto/ActivityResponse` (+ nested MonthlyCount)

### heatmap (3) — 히트맵(5)
- `ActivityHeatmap`: int[7][24] grid(0=월, deep copy) + HeatmapPeak. **임포트 시점 사전계산 결과**임을 주석 명시(리뷰 A)
- `vo/HeatmapPeak`: dayOfWeek(0=월)·hour·count
- `dto/HeatmapResponse`

### search (4) — 검색(6)
- `SearchEntry implements Timestamped`: timestamp + Keyword
- `vo/Keyword`: 빈도 집계 키 VO
- `vo/SearchFrequency`: keyword + count
- `dto/SearchResponse` (+ nested KeywordCount)

### login (3) — 로그인(7)
- `LoginEventType` (enum): LOGIN/LOGOUT
- `LoginEvent implements Timestamped`: timestamp + type + ipAddress + userAgent (문자열만)
- `dto/LoginResponse` (+ nested LoginItem)

### log (3) — 각종 로그(8)
- `LogRecord`: Map<String,String> fields(불변) + nullable timestamp (Timestamped 미구현)
- `LogFile`: fileName + records(불변)
- `dto/MiscLogResponse` (+ nested LogFileView)

### overview (2) — 개요(9)
- `OverviewSummary`: 팔로우·게시물·DM·좋아요·댓글 카운트 + activityFrom/To + mostActiveMonth + importRequired
- `dto/OverviewResponse` (+ nested OverviewData, `importRequired()` 팩토리; domain_exception 5.1)

### explorer (4) — 탐색기(10)
- `ExplorerNode`: name·relativePath·directory·children(불변, 재귀 트리)
- `RawFileContent`: path·content·truncated
- `dto/ExplorerTreeResponse`, `dto/ExplorerFileResponse`

> `*` = 실제 요청 DTO (jakarta `@NotBlank`)

## 4. 리뷰 반영 (계획 v1 → 구현)

| # | 지적 | 반영 |
|---|---|---|
| A | DM 24버킷이라 히트맵 요일축 복원 불가 | `ActivityHeatmap` 주석에 "임포트 시점 transient ts 5종 사전계산 결과" 명시 |
| B | 추상클래스보다 인터페이스 | `Timestamped` 인터페이스 + default 메서드로 구현 |
| C | 배열/컬렉션이 불변성 뚫음 | 방어복사 생성자 + 불변 getter (List.copyOf / 배열 clone / int[][] deep copy) |
| D | 0=월 vs DayOfWeek(MON=1) off-by-one | `EpochMillis.dayOfWeekIndex(ZoneId)` 단일 변환 지점 |
| E | FollowAnalysis가 팔로우 시각 버림 | 결과를 `List<FollowEntry>`로 |
| F | isValid()가 모순 신호 | 제거, `EpochMillis.of()` fail-fast / 검증은 Service 경계 |

## 5. 비고 / 후속

- `vo/` 폴더는 식별자·정규화 VO가 실제 존재하는 `common`/`search`/`heatmap`에만 생성됨
  (최소 VO 정책 결과). follow·message·activity 등은 공통 VO(Username/EpochMillis) 재사용.
  → 필요 시 필드 단위 세분 VO(PostTitle, IpAddress, RoomId 등)로 확장 가능.
- 범위 제외(후속 단계): JSON 파싱 DTO·Jackson 스트리밍 파서, mojibake 디코더, 파일 스캐너,
  Service(@Service 빈)·Controller·예외(ErrorResponse/핸들러). 예외 코드는 `ParseWarningCode` enum으로만 반영.
- 검증: `./gradlew compileJava` 통과. 단위 테스트는 Service 단계에서 작성 예정.
