# Instagram Analyzer : Domain V 1.0

---

# 0. READ ME

**Domain V1.0** 은 `doc/instagram_data.md`, `doc/instagram_menu.md`, `doc/instagram_menu_binding.md`, `doc/instagram_question_answer.md` 를 기반으로 작성함.

> Instagram "내 정보 다운로드" (JSON 포맷) 로 받은 export 데이터를 로컬에서 파싱·분석·시각화하는 앱이다.
> 외부 네트워크 없이 완전 로컬에서 동작하며, 사용자 데이터는 서버로 전송되지 않는다.

**배포·런타임 전제 (설계 전반에 영향)**
- **포터블 단일 실행 앱**이다. 단일 실행 가능한 JAR(프론트엔드는 Spring `static/` 리소스로 동봉) 형태로 배포한다.
- **사용자는 오직 1명**이다. 자신의 PC에서 `localhost` 로 띄워 자기 데이터만 본다.
- 따라서 **인증·로그인·권한(Role)·세션·멀티유저·사용자 격리 개념이 전혀 없다.**
  - 권한 체크(HTTP 403 등)는 "보안"이 아니라 **방어적 안전장치**(예: 폴더 경로 오입력 방지) 목적으로만 최소 사용한다.
- 서버 배포가 아니므로 수평 확장, 동시 다중 요청, 부하 분산 같은 고려는 하지 않는다.

**모든 데이터는 읽기 전용(Read Only)이다.** 사용자가 직접 데이터를 생성·수정·삭제하는 기능은 없으며,
유일한 쓰기 시점은 **임포트(Import)** 단계에서 Spring `@Service` 빈에 메모리로 적재하는 것이다.

**공통 파싱 원칙 (전 도메인 적용)**
1. 경로 하드코딩 금지 — 파일 패턴(`followers_*`, `message_*`, `posts_*`)으로 재귀 스캔
2. Timestamp 정규화 — `connections/` 는 초(seconds), `messages/` 는 밀리초(ms) 혼재 → 전부 epoch ms 로 통일
3. 한글·이모지 깨짐 보정 — 모든 문자열 필드에 `ISO-8859-1 → UTF-8` 재디코딩 처리
4. 미디어 파일 스킵 — `media/` 하위 실제 이미지·동영상 바이너리는 파싱 제외
5. DB 없음 — 최초 1회 파싱 후 메모리(@Service 빈)에 보관, Java Stream 으로 집계

## 0.1 용어 정리

- `본인 / 나 (Owner)` : export 데이터의 주인. 계정 소유자 본인
- `export` : Instagram "내 정보 다운로드" 로 받은 JSON 데이터 묶음(폴더)
- `임포트 (Import)` : export 폴더를 앱이 읽어 메모리에 적재하는 행위
- `epoch ms` : 1970-01-01 UTC 기준 경과 밀리초. 전 도메인 timestamp 통일 단위
- `mojibake` : 인코딩 불일치로 한글·이모지가 깨져 보이는 현상
- `UNKNOWN` : YAML 매핑 사전에 정의되지 않아 분류하지 못한 파일

## 0.2 도메인 전체 지도

| # | 도메인 | 분류 | 자체 파싱 | 비고 |
|---|--------|------|-----------|------|
| 1 | 임포트 (Import) | 인프라 | O | ETL 진입점, 본인 식별 포함 |
| 2 | 팔로우 관계 (Follow) | 관계 | O | 맞팔/짝사랑 집합 연산 |
| 3 | 다이렉트 메시지 (DM) | 관계 | O | 본인 식별 의존 |
| 4 | 활동 기록 (Activity) | 활동 | O | 게시물·좋아요·댓글·저장 |
| 5 | 활동 히트맵 (Heatmap) | 활동 | X | 타 도메인 timestamp 재활용 |
| 6 | 검색 기록 (Search) | 활동 | O | 로그보다 우선 claim |
| 7 | 로그인 기록 (Login) | 보안 | O | IP·기기 문자열 |
| 8 | 각종 로그 (Misc Log) | 보안 | O | 범용 키-값 뷰어 |
| 9 | 개요 대시보드 (Overview) | 집계 | X | 타 도메인 카운트 재활용 |
| 10 | 데이터 탐색기 (Explorer) | 도구 | X | 원본 트리 fallback |

> **자체 파싱 X** 도메인(5·9·10)은 파일을 직접 읽지 않고 다른 도메인의 결과를 재활용한다.

## 0.3 메모리 데이터 저장소

```
파싱된 모든 도메인 객체는 하나의 데이터 보관소(@Service 빈) 안에 들고 있는다.
사용자가 1명뿐이므로 복잡한 동시성 제어는 필요 없다.
```

**비즈니스 로직**
- 데이터는 `@Service` 빈의 필드에 보관하고, 메뉴 조회는 이 메모리에서 읽는다
- **임포트가 진행 중일 때는 프론트엔드 UI 가 메뉴 진입을 막는다** (임포트 화면에 머무름)
  - 단일 사용자라 "조회하면서 동시에 재임포트" 같은 상황이 사실상 발생하지 않음 → 락·원자교체 등 불필요
- 재임포트는 기존 데이터를 새 데이터로 통째 교체한다 (덮어쓰기)
- 임포트는 동기 처리해도 무방하다. 진행률만 프론트로 내려주면 충분하다

---

# 1. 임포트 : Import

**상황**
```
사용자는 Instagram 에서 "내 정보 다운로드" 기능으로 JSON 포맷 데이터를 다운로드한다.
ZIP 압축을 해제한 후, 앱 UI 에서 해당 폴더의 경로를 입력한다.
백엔드는 입력된 경로를 검증하고, 전체 JSON 파일을 스트리밍으로 파싱하여
메모리(Spring @Service 빈)에 도메인 객체로 적재한다.
임포트가 완료되면 모든 메뉴가 활성화된다.
임포트는 앱 사용 중 언제든 재실행할 수 있으며, 재실행 시 기존 메모리 데이터를 덮어쓴다.
```

**비즈니스 로직**
- 사용자가 입력한 경로는 실제 파일시스템에 존재하는 디렉토리여야 한다
- Instagram export 여부 판별을 위해 아래 패턴 중 하나 이상이 재귀 탐색으로 발견되어야 한다
  - `followers_*.json`, `following.json`, `message_*.json`, `posts_*.json`, `login_activity*.json`
- HTML 포맷 export 는 지원하지 않는다 — `.html` 파일만 존재하고 JSON 이 없으면 거부
- 파싱 순서: 팔로우 → 메시지 → 활동(게시물·좋아요·댓글) → 로그인 → 검색 → 로그
- 각 파일은 Jackson Streaming API 로 읽어 메모리 효율을 보장한다
- 파싱 중 오류가 발생한 단일 파일은 스킵하고 경고를 누적하며, 전체 파싱을 중단하지 않는다
- 임포트 완료 시각, 소요 시간, 파싱된 항목 수를 메타데이터로 기록한다
- 임포트 상태는 `IDLE / IN_PROGRESS / COMPLETED / FAILED` 4가지로 관리한다
  - 멀티유저 동기화용이 아니라, **프론트가 진행률·완료 여부를 표시**하기 위한 단순 상태값이다

**조건**
- 경로가 Null / 빈 문자열 / 공백인가?
- 해당 경로가 파일시스템에 존재하는 디렉토리인가?
- Instagram export 식별 패턴이 하나도 발견되지 않았는가?
- HTML 파일만 존재하고 JSON 이 없는가?

**예외**
- 경로 유효성 검사 실패
  : 상태코드 (HTTP 400 : Bad Request) 를 반환하며 구체적인 실패 사유를 알린다
- Instagram export 아님
  : 상태코드 (HTTP 422 : Unprocessable Entity) 를 반환하며 JSON 포맷으로 재다운로드할 것을 안내한다

> 단일 사용자라 "임포트 중복 실행(409 Conflict)" 같은 경합 상황은 사실상 없다.
> 임포트 진행 중에는 프론트 UI 가 재실행 버튼을 비활성화하는 것으로 충분하다.

## 1.1 파일 스캐너 : File Scanner

**상황**
```
임포트 경로가 확정되면 파일 스캐너가 동작한다.
스캐너는 지정된 루트 디렉토리를 재귀적으로 탐색하며,
YAML 매핑 사전(instagram-schema-mapping.yaml)에 정의된 glob 패턴과 일치하는 파일 목록을 수집한다.
실제 이미지·동영상 파일은 수집 단계에서 제외한다.
```

**비즈니스 로직**
- 스캔은 glob 패턴 기반으로 동작한다 (`followers_*.json`, `message_*.json` 등)
- YAML 매핑 사전에 없는 파일은 "미분류(UNKNOWN)" 로 분류하여 데이터 탐색기에서 노출한다
- 확장자가 `.jpg`, `.mp4`, `.png`, `.mov`, `.webp` 인 파일은 스캔 결과에서 제외한다
- 파일 크기가 0 인 경우 스킵하고 경고를 기록한다
- 스캔 결과는 도메인별 파일 목록 Map(`Map<Domain, List<Path>>`)으로 반환한다

**조건**
- 파일 확장자가 미디어 타입(`.jpg`, `.mp4`, `.png`, `.mov`, `.webp`)인가?
- 파일 크기가 0인가?

## 1.2 문자열 정규화 : String Normalizer

**상황**
```
Instagram JSON 은 한글·이모지 등 유니코드 문자를 이스케이프(\uXXXX 혹은 mojibake) 형태로 저장한다.
모든 문자열 필드는 파싱 직후 반드시 정규화를 거쳐야 한다.
```

**비즈니스 로직**
- 모든 문자열 필드는 `ISO-8859-1 → UTF-8` 재디코딩 과정을 거친다
- 정규화는 파싱 단계에서 즉시 수행하며, 이후 메모리에는 정규화된 값만 존재한다
- 정규화 실패(디코딩 불가) 시 원본 값을 그대로 유지하고 경고를 기록한다

## 1.3 Timestamp 정규화 : Timestamp Normalizer

**상황**
```
Instagram export 의 timestamp 단위가 파일 종류에 따라 혼재한다.
connections/ 하위 파일은 초(seconds) 단위, messages/ 하위 파일은 밀리초(ms) 단위이다.
전체 집계(히트맵, Overview) 시 단위가 혼재하면 데이터 오염이 발생한다.
```

**비즈니스 로직**
- 모든 timestamp 는 파싱 즉시 epoch milliseconds(ms) 로 통일한다
- 초(seconds) 판별 기준: 값이 `10_000_000_000L` 미만이면 초 단위로 간주하여 × 1000 변환
- 밀리초(ms) 판별 기준: 값이 `10_000_000_000L` 이상이면 ms 단위로 그대로 사용
- 정규화된 timestamp 는 `Long` 타입으로 저장한다

**조건**
- timestamp 값이 0 이하인가? (비정상 값)
- timestamp 값이 Null 인가?

## 1.4 본인 식별 : Account Identity

**상황**
```
DM 의 보낸/받은 비율, initiator 판별, 팔로우 분석에서 "나(본인)"가 누구인지 알아야 한다.
하지만 어느 username/이름이 본인인지는 대부분의 파일에 명시되어 있지 않다.
임포트 시점에 본인 식별자(username, 표시 이름)를 1회 확정하여 메모리 보관소에 두고,
이후 모든 도메인이 이를 공유한다.
```

**비즈니스 로직**
- 본인 식별 정보는 아래 우선순위로 탐색하여 가장 먼저 발견된 값을 사용한다
  1. `personal_information/personal_information.json` 의 username / 사용자 이름 필드
  2. DM `participants` 교차 분석 — 모든 대화방의 `participants` 에 공통으로 등장하는 이름(= 본인)
  3. 위 둘 다 실패 시 사용자에게 본인 username 을 직접 입력받는다 (수동 fallback)
- 본인 식별자는 `username` 과 `displayName`(표시 이름) 두 형태를 모두 보관한다
  - DM 의 `sender_name` 은 표시 이름과 매칭, 팔로우의 `value` 는 username 과 매칭하기 때문
- 본인 식별은 임포트 단계에서 1회 확정하며, 이후 변경되지 않는다
- `personal_information/` 는 메뉴로 노출하지 않으나 본인 식별 목적으로만 최소 파싱한다

**조건**
- 자동 탐색(1·2)으로 본인 식별에 실패했는가? → 수동 입력 요구
- 수동 입력값이 Null / 빈 문자열 / 공백인가?

**예외**
- 본인 식별 자동 실패
  : 임포트를 `COMPLETED` 로 두되 `ownerResolved: false` 플래그를 세우고,
    본인 식별이 필요한 메뉴(DM 통계)는 본인 username 입력을 먼저 요구한다

---

# 2. 팔로우 관계 : Follow

**상황**
```
사용자는 자신의 팔로워(나를 팔로우하는 사람)와
팔로잉(내가 팔로우하는 사람) 목록을 볼 수 있다.
이 두 목록을 교차 분석하여 맞팔, 내가 짝사랑(안 맞팔), 나를 짝사랑 계정을 도출한다.
최근 언팔로우한 계정, 보낸 팔로우 요청, 친한 친구, 제한 계정도 별도 탭으로 제공한다.
모든 목록은 읽기 전용이며 사용자가 수정할 수 없다.
```

**비즈니스 로직**
- 파싱 대상 파일 패턴
  - 팔로잉: `connections/followers_and_following/following.json` → 루트 키 `relationships_following`
  - 팔로워: `connections/followers_and_following/followers_*.json` → 루트 키 `relationships_followers`
  - 최근 언팔: `recently_unfollowed_profiles.json` → 루트 키 `relationships_unfollowed_users`
  - 보낸 요청: `pending_follow_requests.json` → 루트 키 `relationships_follow_requests_sent`
  - 친한 친구: `close_friends.json` → 루트 키 `relationships_close_friends`
  - 제한 계정: `restricted_profiles.json` → 루트 키 `relationships_restricted_users`
- 각 항목의 공통 스키마: `string_list_data[0].value` = username, `string_list_data[0].timestamp` = 팔로우 시각(초 단위)
- 팔로워 파일은 팔로워 수가 많으면 `followers_1.json`, `followers_2.json` 으로 분할된다 — 전부 합산
- 집계 규칙
  - **맞팔** = following ∩ followers
  - **내가 짝사랑** = following − followers (내가 팔로우하지만 상대방은 나를 팔로우 안 함)
  - **나를 짝사랑** = followers − following (상대방은 나를 팔로우하지만 나는 안 함)
- username 은 대소문자 구분 없이 비교한다 (toLowerCase 정규화)
- timestamp 는 Timestamp Normalizer 를 통해 epoch ms 로 통일

**조건**
- `string_list_data` 배열이 비어있거나 Null 인가?
- `value`(username) 가 Null / 빈 문자열 / 공백인가?
- `timestamp` 가 0 이하이거나 Null 인가?

**예외**
- 파싱 중 스키마 불일치(필수 키 누락)
  : 해당 항목을 스킵하고 경고 로그에 기록, 나머지 정상 항목은 정상 노출

---

# 3. 다이렉트 메시지 : Direct Message

**상황**
```
사용자는 자신의 DM 대화 통계를 볼 수 있다.
대화방 목록, 대화상대별 메시지 수, 내가 보낸/받은 비율, 가장 많이 대화한 상대 Top 10,
대화를 먼저 시작한 비율(initiator), 시간대별 메시지 분포를 보여준다.
대화 원본 텍스트를 그대로 노출하지 않으며, 통계 수치만 제공한다.
모든 데이터는 읽기 전용이다.
```

**비즈니스 로직**
- 파싱 대상: `your_instagram_activity/messages/inbox/{상대방}/message_*.json`
- 대화방 식별: inbox 하위 디렉토리명이 대화방 식별자 (상대방 username 포함)
- 메시지 분할: 대화량이 많으면 `message_1.json`, `message_2.json` 으로 분할 → 전부 합산
- 항목 스키마: `participants[].name`, `messages[].sender_name`, `messages[].timestamp_ms`, `messages[].content`
- `sender_name` 이 본인(나)인지 판별 기준: **본인 식별(1.4)** 에서 확정한 `displayName` 과 일치 여부
- 본인 식별이 안 된 상태(`ownerResolved: false`)면 보낸/받은·initiator 집계는 보류하고 본인 입력을 먼저 요구한다
- 집계 규칙
  - **보낸 메시지**: `sender_name == 나`
  - **받은 메시지**: `sender_name != 나`
  - **initiator**: 각 대화방에서 첫 번째 메시지의 sender_name 이 나인 경우
  - **Top 10**: 전체 메시지 수 기준 내림차순 상위 10개 대화방
  - **시간대 분포**: `timestamp_ms` → 시(0~23) 버킷 카운트
- DM timestamp 는 ms 단위 → Timestamp Normalizer 통과 후에도 그대로 사용
- 대화방 수가 수천 개일 수 있으므로 폴더 순회는 병렬 스트림으로 처리한다
- 실제 메시지 텍스트(content)는 메모리에 보관하지 않는다 — 통계 집계 후 즉시 해제

**조건**
- `participants` 배열이 비어있거나 Null 인가?
- `messages` 배열이 비어있거나 Null 인가?
- `timestamp_ms` 가 0 이하이거나 Null 인가?
- `sender_name` 이 Null / 빈 문자열인가?

**예외**
- 파싱 중 스키마 불일치
  : 해당 대화방을 스킵하고 경고 로그에 기록, 나머지 대화방은 정상 집계

---

# 4. 활동 기록 : Activity

**상황**
```
사용자는 자신이 Instagram 에서 행한 활동(게시물, 좋아요, 댓글, 저장한 게시물)의 이력을 볼 수 있다.
각 활동은 날짜별 타임라인 형태로 제공한다.
실제 이미지·동영상 파일은 파싱하지 않으며, 메타데이터(timestamp, 텍스트)만 다룬다.
모든 데이터는 읽기 전용이다.
```

## 4.1 게시물 : Post

**비즈니스 로직**
- 파싱 대상 파일 패턴
  - 일반 게시물: `your_instagram_activity/media/posts_*.json`
  - 스토리: `your_instagram_activity/media/stories.json`
  - 릴스: `your_instagram_activity/media/reels.json`
- 핵심 필드: `creation_timestamp`(초 단위), `title`(캡션)
- `media[].uri` 에 기록된 미디어 경로는 파싱하지 않는다
- 게시물 파일도 분할(`posts_1.json`, `posts_2.json`)될 수 있으므로 전부 합산한다
- timestamp 는 Timestamp Normalizer 를 통해 epoch ms 로 통일

**조건**
- `creation_timestamp` 가 0 이하이거나 Null 인가?

## 4.2 좋아요 : Like

**비즈니스 로직**
- 파싱 대상 파일 패턴
  - 게시물 좋아요: `your_instagram_activity/likes/liked_posts.json`
  - 댓글 좋아요: `your_instagram_activity/likes/liked_comments.json`
- 핵심 필드: `string_list_data[0].timestamp`(초 단위), `string_list_data[0].href`
- 좋아요 수가 매우 많을 수 있으므로 Jackson Streaming API 로 읽는다

**조건**
- `string_list_data` 배열이 비어있거나 Null 인가?
- `timestamp` 가 0 이하이거나 Null 인가?

## 4.3 댓글 : Comment

**비즈니스 로직**
- 파싱 대상 파일 패턴
  - 일반 게시물 댓글: `your_instagram_activity/comments/post_comments_*.json`
  - 릴스 댓글: `your_instagram_activity/comments/reels_comments.json`
- 핵심 필드: `timestamp`(초 단위), `string_map_data.Comment.value`(댓글 내용)
- 댓글 파일도 분할될 수 있으므로 전부 합산한다
- 댓글 텍스트(content)는 통계 집계(월별 댓글 수 등)에만 사용한다

**조건**
- `timestamp` 가 0 이하이거나 Null 인가?

## 4.4 저장한 게시물 : Saved Post

**비즈니스 로직**
- 파싱 대상: `your_instagram_activity/saved/saved_posts.json`
- 핵심 필드: `string_list_data[0].timestamp`(초 단위), `string_list_data[0].href`

**조건**
- `timestamp` 가 0 이하이거나 Null 인가?

---

# 5. 활동 히트맵 : Activity Heatmap

**상황**
```
사용자의 모든 활동(게시물, 좋아요, 댓글, DM, 로그인)의 timestamp 를 하나로 합산하여
요일(7) × 시간(24) 그리드로 시각화한다.
"나는 화요일 밤 11시에 가장 활발하다" 와 같은 인사이트를 제공한다.
히트맵은 자체 파일을 파싱하지 않으며, 위 도메인들이 적재한 데이터의 timestamp 를 재활용한다.
모든 데이터는 읽기 전용이다.
```

**비즈니스 로직**
- 입력 소스: 게시물, 좋아요, 댓글, DM 메시지, 로그인 이벤트의 `epoch ms` timestamp 전체
- 변환 규칙: 각 timestamp → 요일(0=월 ~ 6=일) × 시(0~23) 버킷에 카운트 +1
- 시간대 기준은 **시스템 로컬 시간(KST)** 으로 변환한다
- 결과 자료구조: `int[7][24]` 이차원 배열로 집계
- 집계는 임포트 완료 시점에 1회 계산하여 캐싱한다 (메뉴 클릭마다 재계산하지 않음)
- 각 도메인에서 Timestamp Normalizer 를 통과한 epoch ms 값만 사용한다 (원본 재파싱 없음)

**조건**
- 임포트가 완료(`COMPLETED`) 상태가 아닌데 히트맵 조회를 요청하는가?

**예외**
- 임포트 미완료
  : 상태코드 (HTTP 503 : Service Unavailable) 를 반환하며 임포트 먼저 진행할 것을 안내한다

---

# 6. 검색 기록 : Search History

**상황**
```
사용자가 Instagram 앱에서 검색했던 키워드(계정, 해시태그, 위치 등)의 이력을 보여준다.
검색어 빈도를 집계하여 워드클라우드 또는 빈도 테이블로 시각화한다.
모든 데이터는 읽기 전용이다.
```

**비즈니스 로직**
- 파싱 대상 파일 패턴 (export 버전에 따라 경로가 다를 수 있음)
  - `your_instagram_activity/` 하위 검색 history 파일
  - `logged_information/` 하위 검색 history 파일
  - 파일명 패턴: `*search*history*.json`, `account_searches*.json`
- 핵심 필드: 검색어(텍스트), `timestamp`
- 검색어 집계: 동일 검색어 빈도 내림차순 정렬
- 검색어는 문자열 정규화(String Normalizer) 를 거친다
- Instagram export 버전에 따라 루트 키가 다를 수 있으므로 YAML 매핑 사전으로 관리한다
- **claim 우선순위**: `logged_information/` 내 검색 history 파일은 검색 기록(6)이 **먼저 claim** 하며,
  각종 로그(8)는 검색 기록이 가져가지 않은 나머지 파일만 처리한다 (이중 집계 방지)

**조건**
- 검색어가 Null / 빈 문자열 / 공백인가?
- `timestamp` 가 0 이하이거나 Null 인가?

**예외**
- 검색 기록 파일이 export 에 존재하지 않는 경우
  : 빈 결과를 반환하고 "해당 데이터가 없습니다" 를 안내한다

---

# 7. 로그인 기록 : Login History

**상황**
```
사용자의 로그인 이벤트 이력(로그인 시각, 접속 기기, IP 주소)을 시간순으로 보여준다.
이상 접속(알 수 없는 기기, 해외 IP 등)을 사용자가 직접 확인할 수 있도록 돕는다.
IP 주소는 있는 그대로 표시하며, 지역 정보 변환은 하지 않는다.
모든 데이터는 읽기 전용이다.
```

**비즈니스 로직**
- 파싱 대상 파일 패턴
  - `security_and_login_information/login_and_profile_creation/login_activity*.json`
  - `security_and_login_information/login_and_profile_creation/logout_activity*.json`
- 핵심 필드: `timestamp`(초 단위), `ip_address`, `user_agent`(기기·브라우저 정보)
- 로그인과 로그아웃은 이벤트 타입으로 구분한다 (`LOGIN` / `LOGOUT`)
- `ip_address`, `user_agent` 는 오직 문자열로만 취급한다 (파싱·분석 없음)
- 목록은 timestamp 내림차순(최신 순)으로 정렬한다
- timestamp 는 Timestamp Normalizer 를 통해 epoch ms 로 통일

**조건**
- `timestamp` 가 0 이하이거나 Null 인가?
- `ip_address` 가 Null / 빈 문자열인가?

**예외**
- 로그인 기록 파일이 export 에 존재하지 않는 경우
  : 빈 결과를 반환하고 "해당 데이터가 없습니다" 를 안내한다

---

# 8. 각종 로그 : Misc Log

**상황**
```
logged_information/ 하위에 존재하는 기타 활동 로그를 보여준다.
파일 구조가 계정마다 달라 고정 스키마 파싱이 불가능하므로,
키-값 형태의 범용 로그 뷰어로 처리한다.
타임스탬프 기준 내림차순 정렬로 보여준다.
모든 데이터는 읽기 전용이다.
```

**비즈니스 로직**
- 파싱 대상: `logged_information/` 하위 JSON 파일 중 **검색 기록(6)이 claim 하지 않은 나머지 전부**
- 고정 스키마 없음 — 파일 내 최상위 키를 컬럼명으로, 값을 문자열로 변환하여 행으로 표현한다
- timestamp 로 인식 가능한 필드(`timestamp`, `time`, `date` 포함 키)가 있으면 날짜 변환 후 표시한다
- 모든 값은 문자열로 변환하여 표시한다 (타입 추론 없음)
- 파일별로 그룹화하여 표시한다 (어느 파일에서 온 로그인지 구분)

**조건**
- `logged_information/` 디렉토리 자체가 export 에 없는가?

**예외**
- 디렉토리 미존재
  : 빈 결과를 반환하고 "해당 데이터가 없습니다" 를 안내한다
- 파싱 중 JSON 형식 오류
  : 해당 파일을 스킵하고 "파싱 실패" 로 표시한다

---

# 9. 개요 대시보드 : Overview Dashboard

**상황**
```
앱 진입 시 가장 먼저 보이는 화면이다.
각 도메인의 집계 결과를 카드 형태로 요약 표시한다.
임포트가 완료되지 않은 경우 임포트 유도 화면을 보여준다.
개요 대시보드는 자체 파일을 파싱하지 않으며, 다른 도메인의 집계 결과를 재활용한다.
```

**비즈니스 로직**
- 표시 항목
  - 총 팔로워 수 / 총 팔로잉 수 / 맞팔 수 / 내가 짝사랑 수
  - 총 게시물 수 (게시물 + 스토리 + 릴스)
  - 총 DM 대화방 수 / 총 메시지 수
  - 총 좋아요 수 / 총 댓글 수
  - 계정 활동 기간 (가장 오래된 timestamp ~ 가장 최근 timestamp)
  - 가장 활발했던 달 (월별 활동 수 최댓값)
- 모든 수치는 각 도메인 Service 에서 제공하는 카운트를 조합한다
- 임포트 상태가 `COMPLETED` 가 아니면 숫자 대신 임포트 유도 메시지를 표시한다

**조건**
- 임포트가 완료(`COMPLETED`) 상태가 아닌데 대시보드 조회를 요청하는가?

**예외**
- 임포트 미완료
  : 임포트 유도 화면을 응답한다 (상태코드 HTTP 200, 빈 데이터 + `importRequired: true` 플래그)

---

# 10. 데이터 탐색기 : Data Explorer

**상황**
```
사용자가 임포트한 폴더의 전체 디렉토리 트리를 탐색할 수 있다.
각 JSON 파일을 선택하면 원본 JSON 을 그대로 볼 수 있다.
메인 메뉴가 파싱하지 못한 "미분류(UNKNOWN)" 파일을 확인하거나,
인스타그램 스키마가 변경되어 새로운 파일이 생겼을 때 직접 확인하는 용도이다.
실제 이미지·동영상 파일은 트리에서 제외한다.
모든 데이터는 읽기 전용이다.
```

**비즈니스 로직**
- 루트는 임포트 시 지정된 폴더 경로
- 확장자가 `.jpg`, `.mp4`, `.png`, `.mov`, `.webp` 인 파일은 트리에서 제외한다
- JSON **구조(키·계층·배열)는 원본 그대로** 보존하여 반환한다 (집계·필드 가공 없음)
- 단, 문자열 값은 String Normalizer 를 적용해 한글·이모지가 깨지지 않게(mojibake 방지) 보정한다
  - 즉 "구조는 raw, 표시는 사람이 읽을 수 있게" — raw 바이트 그대로 깨진 채 내려주지 않는다
- 단일 파일 응답 크기가 10MB 를 초과하면 처음 10MB 만 반환하고 truncated 플래그를 함께 응답한다
- 디렉토리 트리는 최대 깊이 10단계까지 탐색한다

**조건**
- 요청 경로가 임포트 루트 외부를 가리키는가? (외부 공격 방어가 아니라, 잘못된 경로로 엉뚱한 파일을 읽는 실수 방지)
- 요청 파일이 미디어 타입인가?

**예외**
- 루트 외부 경로 접근
  : 상태코드 (HTTP 400 : Bad Request) 를 반환한다 (루트 밖은 탐색 대상이 아님)
- 파일 미존재
  : 상태코드 (HTTP 404 : Not Found) 를 반환한다

---

# 부록 A. 도메인 ↔ REST 엔드포인트 (제안)

> 모든 엔드포인트는 `localhost` 에서만 호출되는 로컬 API 다. 인증 헤더·토큰·CORS 설정이 필요 없다.

| 도메인 | 메서드 · 경로 | 비고 |
|--------|--------------|------|
| 임포트 | `POST /api/import` (body: 폴더 경로) | ETL 실행 |
| 임포트 상태 | `GET /api/import/status` | IDLE/IN_PROGRESS/COMPLETED/FAILED + ownerResolved |
| 본인 식별 수동 | `POST /api/import/owner` (body: username) | 자동 실패 시 fallback |
| 팔로우 | `GET /api/follows?type=mutual\|i_follow\|follows_me\|unfollowed\|...` | type 으로 분기 |
| DM | `GET /api/messages/stats` | Top10·비율·시간대 |
| 활동 | `GET /api/activity?type=post\|like\|comment\|saved` | |
| 히트맵 | `GET /api/heatmap` | int[7][24] |
| 검색 | `GET /api/searches` | 빈도 집계 |
| 로그인 | `GET /api/logins` | 최신순 |
| 각종 로그 | `GET /api/logs` | 파일별 그룹 |
| 개요 | `GET /api/overview` | 카드 집계 |
| 탐색기 트리 | `GET /api/explorer/tree` | 디렉토리 트리 |
| 탐색기 파일 | `GET /api/explorer/file?path=...` | 루트 외부 차단 |

# 부록 B. export 구조 버전 대응

```
2020 구조(평면) :  connections.json, messages.json 등 최상위 평면 배치
2025~26 구조(중첩) :  connections/, your_instagram_activity/ 등 폴더 중첩
```

- 두 구조 모두 **재귀 스캔 + glob 패턴**으로 동일하게 흡수한다 (경로 깊이에 무관)
- 따라서 도메인 명세의 경로(예: `your_instagram_activity/media/posts_*.json`)는 **참고용 전체 경로**이며,
  실제 매칭은 파일명 패턴(`posts_*.json`)으로 수행한다
- 새 구조에서 키 이름이 바뀌면 코드 수정 없이 `instagram-schema-mapping.yaml` 만 갱신한다

# 부록 C. 시간대(Timezone) 정책

- 히트맵·월별 집계의 요일/시 변환 기준 시간대는 **앱이 실행되는 PC 의 시스템 로컬 시간**을 따른다
- 포터블·단일 사용자 앱이므로 사용자 본인 PC 시간대 = 본인 활동 시간대로 자연히 일치한다 (별도 설정 불필요)
- epoch ms → 로컬 시간 변환은 단일 유틸을 통과시켜 도메인 간 일관성을 보장한다
