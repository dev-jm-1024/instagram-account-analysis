# Instagram Question Answer : Claude Code (Opus)

---

## Q1. 프론트엔드에서 메뉴를 조회하면 결국 디스크에서 읽어서 프론트엔드에 렌더링해야 하는 거 아니야?

맞습니다. 흐름은 다음과 같습니다.

```
[디스크: 압축 푼 폴더]  →  [Spring Boot: 읽고 집계]  →  [JSON 응답]  →  [React: 렌더링]
   2GB 원본                미디어 스킵, 패턴 스캔        작은 결과만        차트/표
```

프론트엔드(브라우저)는 디스크를 직접 읽을 수 없습니다(브라우저 샌드박스 제약).
따라서 **백엔드가 디스크 ↔ 프론트엔드 사이의 중개자** 역할을 합니다.

사용자가 메뉴 클릭 → 프론트가 `GET /api/follows` 호출 → 백엔드가 파싱된 데이터 집계 → 결과 JSON만 응답.

---

## Q2. 네트워크 연결 필요 없는 DB 방식으로 접근해야 하나?

### 순수 프론트엔드(File System Access API) 방식은 왜 안 되나?

- `showDirectoryPicker()` API는 Firefox 미지원, Safari 제한적 — 브라우저 호환성 문제
- 2GB 폴더를 브라우저 메모리에 올리면 탭 크래시 위험
- 히트맵 집계, 팔로우 교집합 연산 등 무거운 연산을 브라우저 JS로 처리하기에는 무리
- 이미 Spring Boot 백엔드가 세팅되어 있으므로 버리는 구조

**결론: 백엔드가 디스크를 읽어 API로 내려주는 방식이 정답.**

---

## Q3. 굳이 DB를 연동해야 할까?

### 필요 없습니다.

이 프로젝트의 특성을 보면 DB가 정당화되지 않습니다.

| DB가 필요한 조건 | 이 프로젝트 해당 여부 |
|-----------------|----------------------|
| 데이터가 자주 변경됨 | X — Instagram export는 읽기 전용 스냅샷 |
| 다수 프로세스가 동시 접근 | X — 로컬 단일 사용자 앱 |
| 복잡한 ad-hoc 쿼리 필요 | X — 메뉴가 고정되어 있고 쿼리 패턴이 정해져 있음 |
| 트랜잭션/정합성 필요 | X — 쓰기 작업 자체가 없음 |

또한 실제 유용한 데이터 크기는 생각보다 작습니다.
2GB처럼 보이지만 대부분은 미디어 파일(사진, 동영상)이고,
텍스트 데이터(팔로워 목록, DM 내용, 댓글, 로그인 기록)만 파싱하면
Java heap에 충분히 들어갑니다.

### 채택할 구조: 최초 1회 파싱 → 메모리 보관 → Stream 집계

```
앱 시작 or 폴더 경로 입력
        ↓
전체 JSON 파싱 (1회, 수십 초 소요)
        ↓
Spring @Service 빈이 파싱된 도메인 객체를 필드로 보유
        ↓
메뉴 클릭 → Service가 Java Stream으로 집계/필터 → JSON 응답
```

```java
@Service
public class InstagramDataStore {
    private List<Follow> following;
    private List<Follow> followers;
    private List<Message> messages;
    private List<Post> posts;
    private List<LoginEvent> logins;
    // 파싱 완료 후 메모리에 보유
}
```

파일을 다시 읽는 것도 없고, DB 스키마도 없고, ORM도 없습니다.
**가장 단순하고 이 프로젝트에 맞는 구조입니다.**

### DB 도입을 재검토해야 할 시점 (나중에)

- DM 대화방이 수만 개 이상이거나 메시지가 수천만 건 이상
- 재시작 시 재파싱이 10분 이상 걸리는 경우

이 조건에 해당하면 그때 H2 file 모드를 추가해도 늦지 않습니다.

---

## Q4. JSON 스키마를 보고 뜻을 알아낼 수 있을까?

두 가지 문제를 분리해야 합니다.

### (a) 구조 파악 — 자동화 가능

어떤 키가 있고 타입이 뭔지는 기계가 추론할 수 있습니다.

```json
// posts_1.json 예시
{
  "media": [
    {
      "creation_timestamp": 1700000000,
      "title": "...",
      "uri": "media/posts/..."
    }
  ]
}
// → "creation_timestamp는 Long, 초 단위, 게시 시각"으로 추론 가능
```

샘플 파일을 읽어서 키 목록, 타입, 예시값을 뽑는 **스키마 인스펙터**가 이 역할을 합니다.
`데이터 탐색기` 메뉴가 이 역할을 담당합니다.

### (b) 의미 파악 — 자동 불가, 사전(매핑 테이블) 필요

`relationships_following`이 "내가 팔로우하는 사람"이라는 **뜻**은 JSON 어디에도 적혀 있지 않습니다.
이는 Instagram이 정한 약속이므로, 한 번 조사해서 **매핑 사전을 코드에 정의해야** 합니다.

### 매핑 사전은 YAML 설정 파일로 분리

Instagram은 export 구조를 자주 변경합니다.
Java 코드에 하드코딩하면 구조가 바뀔 때마다 재컴파일이 필요합니다.
대신 외부 YAML 파일로 분리하면 파일만 수정하면 됩니다.

```yaml
# instagram-schema-mapping.yaml (resources에 포함)
followers:
  pattern: "followers_*.json"
  root_key: "relationships_followers"
  item_path: "string_list_data[0]"
  fields:
    username: "value"
    timestamp: { key: "timestamp", unit: "seconds" }

messages:
  pattern: "message_*.json"
  root_key: "messages"
  fields:
    sender: "sender_name"
    timestamp: { key: "timestamp_ms", unit: "millis" }
    content: "content"
```

### 현실적 전략: 알려진 키 + 미분류 fallback

- **알려진 키**: YAML 매핑 사전에 등록된 키 → 정확히 파싱하여 메인 메뉴에 표시
- **모르는 키**: YAML에 없으면 → `데이터 탐색기`에서 구조만 보여주고 "미분류" 표시

이렇게 하면 Instagram이 구조를 바꿔서 새 키가 생겨도 앱이 죽지 않고,
점진적으로 사전을 키워나갈 수 있습니다.

---

## 공통 설계 원칙 요약

1. **경로 하드코딩 금지** — `followers_*`, `message_*` glob 패턴으로 재귀 스캔
2. **Timestamp 정규화** — `connections/`는 초(seconds), `messages/`는 밀리초(ms) 혼재 → epoch ms로 통일
3. **한글 깨짐 보정** — 모든 문자열 필드에 `ISO-8859-1 → UTF-8` 재디코딩 처리
4. **미디어 파일 스킵** — `media/` 폴더 실제 이미지/동영상은 파싱 제외 (2GB 주범)
5. **DB 없이 메모리** — 최초 1회 파싱 후 `@Service` 빈에 보관, Java Stream으로 집계
6. **매핑 사전 YAML 분리** — 스키마 변경에 유연하게 대응
