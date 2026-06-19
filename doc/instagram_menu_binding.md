## Gemini

---

앞서 구상한 메뉴 구조를 실제 인스타그램 추출 데이터(JSON 포맷 기준)와 매핑한 테이블입니다. 백엔드에서 데이터 모델(DTO)을 설계하거나 데이터를 파싱할 때 이 구조를 참고하시면 수월합니다.

### 🗂️ 메뉴 및 디렉토리 바인딩 맵

| 대메뉴 (Menu) | 세부 기능 (Feature) | 타겟 디렉토리 (Directory) | 주요 바인딩 파일 (JSON) |
| --- | --- | --- | --- |
| **1. 인적 네트워크** | 팔로워 / 팔로잉 목록 | `followers_and_following` | `followers.json`, `following.json` |
|  | 친한 친구 및 숨김 계정 | `followers_and_following` | `close_friends.json`, `hide_story_from.json` |
| **2. 다이렉트 메시지** | DM 텍스트 대화 내역 | `messages/inbox` | 각 대화 상대방 폴더 내의 `message_1.json` |
|  | DM 첨부 미디어 | `messages/inbox` | 대화 폴더 내 `photos/`, `videos/` 등 |
| **3. 소통 및 활동** | 남긴 댓글 및 좋아요 | `comments`, `likes` | `post_comments.json`, `liked_posts.json` |
|  | 스토리 상호작용 (투표 등) | `story_sticker_interactions` | `polls.json`, `questions.json` |
| **4. 미디어 갤러리** | 내 게시물 / 스토리 백업 | `content`, `media` | `posts.json`, `stories.json` 및 `media/` 하위 원본 파일 |
|  | 북마크(저장한 게시물) | `saved` | `saved_posts.json` |
| **5. 시스템 및 보안 로그** | 로그인 및 접속 기기 기록 | `login_and_account_creation` | `login_activity.json` |
|  | 검색 기록 등 기타 로그 | `recent_searches` | `account_searches.json` |

---

### 💡 데이터 바인딩 시 핵심 고려사항

* **메모리 관리 및 파싱:** `messages`나 `likes`와 같은 파일은 사용자의 활동량에 따라 파일 크기가 매우 방대할 수 있습니다. 서버에서 이 대용량 JSON을 한 번에 메모리에 올리기보다는, 스트리밍 방식으로 읽거나 적절히 분할 처리하여 가비지 컬렉터(GC)에 가해지는 부담을 줄이는 설계가 필요합니다.
* **텍스트 인코딩 복원:** 인스타그램에서 내려받은 JSON 파일은 한글이나 이모지 등의 유니코드 문자가 이스케이프(`\u00eb` 같은 형태)되어 깨져 보이는 경우가 잦습니다. 파싱 단계에서 이를 정상적인 UTF-8 문자열로 디코딩해 주는 전처리 로직을 반드시 추가해야 화면에 정상적으로 출력됩니다.
* **미디어 경로 연결:** `posts.json` 등의 파일 안에는 실제 이미지나 영상의 절대적인 바이너리 데이터가 아닌, 로컬 상대 경로(`media/posts/2026/...`)가 텍스트로 적혀 있습니다. 이를 화면에 띄우려면 추출한 디렉토리를 정적 파일(Static Resource) 경로로 마운트하고, JSON의 경로 값을 매핑해 주어야 합니다.

---

## Claude

전체 메뉴(코어 4 + 추천 3 + 보너스 3) 기준으로, 각 메뉴가 어떤 디렉토리/파일을 읽는지 전부 바인딩해 정리했습니다. 실제 export 구조 변동성을 고려해 **경로는 "패턴"으로** 적었어요 (하드코딩 금지 원칙).

## 메뉴 ↔ 디렉토리/파일 바인딩 표

**📊 Overview (요약 대시보드)**
- 소스: 아래 모든 메뉴의 집계 결과를 재활용 (자체 파일 없음)
- 읽는 것: 각 파서가 뱉은 카운트/날짜범위만 모아서 카드로
- 산출: 총 팔로워/팔로잉, 총 게시물·DM·좋아요·댓글 수, 계정 사용기간(최초~최근 timestamp), 가장 활발한 달

---

**👥 팔로우 보기**
| 파일 패턴 | 의미 | JSON 루트 키 |
|---|---|---|
| `connections/followers_and_following/following.json` | 내가 팔로우 | `relationships_following` |
| `.../followers_*.json` (분할) | 나의 팔로워 | `relationships_followers` |
| `.../recently_unfollowed_profiles.json` | 최근 언팔 | `relationships_unfollowed_users` |
| `.../pending_follow_requests.json` | 보낸 요청 대기 | `relationships_follow_requests_sent` |
| `.../close_friends.json` | 친한친구 | `relationships_close_friends` |
| `.../restricted_profiles.json` | 제한 계정 | `relationships_restricted_users` |
- 공통 항목 스키마: `string_list_data:[{value:username, href, timestamp}]`
- 집계: following−followers = **내가짝사랑(안맞팔)**, followers−following = **나를 짝사랑**, 교집합 = 맞팔

---

**💬 DM 통계**
| 파일 패턴 | 의미 |
|---|---|
| `your_instagram_activity/messages/inbox/{상대}/message_1.json` (방마다, 분할되면 message_2…) | 대화 내용 |
| `.../messages/inbox/{상대}/` 폴더명 | 대화 상대 식별 |
- 항목 스키마: `participants:[name]`, `messages:[{sender_name, timestamp_ms, content}]`
- 집계: 방별 메시지수, 보낸/받은 비율, 상대 Top10, 먼저 말건 비율(initiator), 시간대 분포
- ⚠️ 폴더 수천 개 → 병렬 스트림 처리 대상

---

**📝 활동 기록 (게시물·좋아요·댓글)**
| 파일 패턴 | 의미 | 핵심 필드 |
|---|---|---|
| `your_instagram_activity/media/posts_*.json` | 올린 게시물 | `creation_timestamp`, `media[]` |
| `.../media/stories.json` | 스토리 | `creation_timestamp` |
| `.../media/reels.json` | 릴스 | `creation_timestamp` |
| `.../likes/liked_posts.json` | 좋아요한 게시물 | `string_list_data[].timestamp` |
| `.../likes/liked_comments.json` | 좋아요한 댓글 | timestamp |
| `.../comments/post_comments_*.json` | 내가 단 댓글 | `timestamp`, content |
| `.../comments/reels_comments.json` | 릴스 댓글 | timestamp |
| `.../saved/saved_posts.json` | 저장한 글 | timestamp |
- ⚠️ `media/` 폴더의 실제 사진·동영상 파일은 **스킵** (2GB 주범, 분석 불필요)

---

**🔥 활동 히트맵 (시간대)**
- 소스: **위 활동기록 + DM + 로그인의 모든 timestamp를 한데 모음** (자체 파일 없음)
- 변환: 각 timestamp → 요일(0~6) × 시(0~23) 버킷 카운트
- 주의: timestamp 단위 혼재 — connections/activity는 **초(s)**, messages는 **밀리초(ms)**. 통일 필요

---

**🔍 검색 기록**
| 파일 패턴 | 의미 |
|---|---|
| `your_instagram_activity/`(버전따라 `logged_information/`) 내 검색 history 파일 | 계정/태그/장소 검색어 |
- 루트 키 예: `searches_*` / `search_history`
- 집계: 검색어 빈도(워드클라우드), 검색 시점 분포

---

**🔐 로그인 기록**
| 파일 패턴 | 의미 |
|---|---|
| `security_and_login_information/login_and_profile_creation/login_activity.json`(또는 유사명) | 로그인 이벤트 |
| `.../logout_activity.json` | 로그아웃 |
| `.../login_activity_*` / `account_activity` | 세션 |
- 항목: 시각(timestamp), IP, 기기/User-Agent
- 집계: 로그인 타임라인, IP별/기기별 빈도, (가능시) IP→지역

---

**📋 각종 로그**
| 파일 패턴 | 의미 |
|---|---|
| `logged_information/` 하위 전체 | 기타 활동 로그(기기 정보, 활동 로그 등) |
- 구조가 비표준·계정별 편차 큼 → **고정 파싱보다 "범용 로그 뷰어"**(키-값 테이블 + timestamp 정렬)로 처리 권장

---

**🗂 데이터 탐색기 (Raw Viewer)**
- 소스: ZIP/폴더 **전체 트리** (미디어 제외 옵션)
- 기능: 디렉토리 트리 탐색 → 선택한 JSON 원본 표시. 위 메뉴가 못 잡은 파일 대응용 fallback

---

**설계에 박아둘 공통 규칙 3가지**

1. **패턴 매칭 스캐너**: 경로 하드코딩 X. `followers_*`, `message_*`, `posts_*`, `*_comments_*` 같은 glob으로 재귀 탐색 → 폴더 구조 바뀌어도 생존
2. **timestamp 정규화**: 초/밀리초 혼재 → 전부 epoch ms로 통일하는 단일 유틸 통과
3. **한글 mojibake 보정**: 모든 문자열 필드는 `ISO-8859-1 → UTF-8` 재디코딩 유틸 통과 (DM·닉네임·검색어 깨짐 방지)

---



