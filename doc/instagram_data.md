## Gemini

---

인스타그램에서 '내 정보 다운로드(데이터 내보내기)' 기능을 통해 데이터를 추출하고 압축을 풀면, 사용자가 선택한 포맷(주로 **HTML** 또는 **JSON**)에 따라 수많은 폴더(디렉토리)와 파일들이 생성됩니다.

메타(Meta)의 업데이트에 따라 폴더명이나 구조가 조금씩 변경될 수 있지만, 핵심적인 주요 디렉토리와 각각의 역할은 다음과 같습니다.

### 📁 주요 디렉토리 및 역할

* **`media` (또는 `content`)**
* **역할:** 사용자가 인스타그램에 업로드한 모든 실제 이미지 및 동영상 파일이 원본에 가까운 형태로 저장되어 있는 곳입니다.
* **세부 내용:** 게시물(Posts), 스토리(Stories), 릴스(Reels), 프로필 사진(Profile) 등이 각각의 하위 폴더로 나뉘어 저장됩니다.


* **`messages`**
* **역할:** 다른 사용자와 주고받은 다이렉트 메시지(DM) 내역입니다.
* **세부 내용:** 각 대화방(채팅 상대방 이름)별로 폴더가 생성되며, 텍스트 대화 내용뿐만 아니라 채팅창에서 주고받은 사진, 동영상, 음성 메시지 파일도 함께 포함됩니다.


* **`followers_and_following` (또는 `connections`)**
* **역할:** 사용자 계정의 인적 네트워크 연결 상태를 보여주는 데이터입니다.
* **세부 내용:** 현재 나를 팔로우하는 사람, 내가 팔로우하는 사람, 차단한 계정, 숨김 처리한 계정, '친한 친구' 목록, 보냈으나 수락되지 않은 팔로우 요청 등의 목록이 텍스트 형태로 들어있습니다.


* **`comments`**
* **역할:** 사용자가 인스타그램 활동 중 남긴 모든 댓글의 기록입니다.
* **세부 내용:** 일반 게시물 댓글, 릴스 댓글 등 사용자가 작성한 텍스트와 작성 시간 정보가 담겨 있습니다.


* **`likes`**
* **역할:** 사용자가 '좋아요(하트)'를 누른 모든 활동 내역입니다.
* **세부 내용:** 좋아요를 누른 게시물과 댓글의 목록이 정리되어 있습니다.


* **`saved`**
* **역할:** 인스타그램에서 북마크 아이콘을 눌러 '저장'한 게시물 데이터입니다.
* **세부 내용:** 저장한 게시물 목록과 사용자가 직접 분류해 둔 컬렉션(폴더) 정보가 포함됩니다.


* **`recent_searches` (또는 `searches`)**
* **역할:** 검색 탭에서 사용자가 입력했던 검색 기록입니다.
* **세부 내용:** 검색했던 계정, 해시태그, 위치 등의 텍스트 데이터가 시간순으로 기록되어 있습니다.


* **`login_and_account_creation`**
* **역할:** 계정의 보안 및 접속 이력과 관련된 시스템 데이터입니다.
* **세부 내용:** 계정 생성일, 최근 로그인/로그아웃 시간, 접속한 기기 정보(IP 주소, 스마트폰 모델 등)가 기록되어 있습니다.


* **`personal_information` (또는 `information_about_you`)**
* **역할:** 계정에 등록된 기본적인 프로필 설정 데이터입니다.
* **세부 내용:** 가입 이메일, 전화번호, 생년월일, 성별, 프로필 이름 및 소개글의 변경 내역 등이 포함됩니다.


* **`ads_and_businesses`**
* **역할:** 인스타그램 알고리즘이 파악한 사용자의 관심사 및 광고 반응 데이터입니다.
* **세부 내용:** 클릭한 광고 목록, 인스타그램이 분류한 사용자의 '관심 주제(Interests)' 목록 등을 확인할 수 있습니다.


* **`story_sticker_interactions`**
* **역할:** 다른 사람의 스토리에서 상호작용한 내역입니다.
* **세부 내용:** 투표, 질문 답변, 감정 슬라이더, 퀴즈 등에 참여한 기록이 담겨 있습니다.



---

### 💡 추가 참고 사항

**다운로드 포맷 선택 팁:**
데이터를 요청할 때 **HTML** 포맷을 선택하면 별도의 프로그램 없이 웹 브라우저를 통해 눈으로 쉽게 읽고 클릭해 볼 수 있도록 정리되어 나옵니다. 반면, **JSON** 포맷을 선택하면 텍스트 기반의 구조화된 데이터로 제공되어 프로그래밍(예: 파이썬 등)을 통한 데이터 분석이나 백업 자동화 처리에 훨씬 용이합니다.

---

## Claude

Instagram JSON export의 디렉토리 구조를 더 정밀하게 조사하겠습니다.조사 결과를 종합해서 **Instagram JSON export 전체 디렉토리 지도**를 정리했습니다. (주의: 인스타가 구조를 자주 바꾸고 계정마다 일부 폴더가 없을 수 있어, 경로 하드코딩보다 패턴 매칭이 안전합니다.)

## Instagram JSON Export 디렉토리 전체 구조

압축을 풀면 최상위에 보통 다음 디렉토리들이 나옵니다. 각 역할과 핵심 파일을 함께 정리했어요.

**1. `connections/` — 인맥 관계 (분석 핵심)**
- `followers_and_following/following.json` → 내가 팔로우하는 계정
- `followers_and_following/followers_1.json`, `followers_2.json`... → 나를 팔로우하는 계정 (팔로워가 많으면 여러 파일로 분할됨)
- `followers_and_following/close_friends.json` → 친한 친구 목록
- `followers_and_following/pending_follow_requests.json` → 보낸 팔로우 요청 대기중
- `followers_and_following/recently_unfollowed_profiles.json` → 최근 언팔로우한 프로필 (있으면 언팔 분석에 직접 쓰임)
- `followers_and_following/restricted_profiles.json` → 제한한 계정
- 각 항목 공통 스키마: `string_list_data: [{href, value(username), timestamp}]`

**2. `your_instagram_activity/` — 활동 기록 (가장 방대, 미디어 포함)**
- `messages/inbox/{상대}/message_1.json` → 대화방별 폴더, 보통 상대 username으로 명명, 내부 message_1.json에 대화 내용
- `media/posts_1.json` → 내가 올린 게시물 (`creation_timestamp`, `media[]`)
- `media/` 폴더 → 실제 사진·동영상 파일 (**2GB의 대부분, 분석엔 불필요**)
- `likes/liked_posts.json` → 좋아요 누른 게시물
- `comments/post_comments_1.json` → 내가 단 댓글 전체
- `threads.json` → Threads 게시물
- `saved/` → 저장한 게시물
- 검색기록 (search history)

**3. `security_and_login_information/` — 보안/로그인 (메뉴: 로그인 기록)**
- 로그인 기록, 사용 기기, IP 주소, 비밀번호 변경 등 보안 이벤트
- 로그인 활동, 로그아웃 활동 파일들

**4. `personal_information/` — 계정 정보 (메뉴: 프로필)**
- 프로필 데이터, 이메일, 전화번호, 연결된 계정, 계정 설정 변경 이력

**5. `ads_information/` — 광고**
- 관심사 기반 광고 토픽, 상호작용한 광고

**6. `apps_and_websites_off_of_instagram/` — 외부 연동**
- 인스타 밖 앱·웹사이트 연동 활동

**7. `logged_information/` — 각종 로그**
- 기타 활동 로그

**8. `preferences/` — 설정**
- 앱 환경설정

---

**중요한 함정 두 가지 (설계에 반영해야 함)**

1. **HTML로 받으면 못 씀** — HTML로 이미 받았다면 JSON 포맷으로 다시 요청해야 함. 기존 HTML export는 파싱 도구에서 작동 안 함. 사용자 안내 필수.

2. **구조가 계정/연도마다 다름** — 위 doc 14·16의 2020년 구조(`connections.json`, `messages.json` 평면 구조)와 현재(2025-26) 구조(`connections/`, `your_instagram_activity/` 중첩)가 완전히 다릅니다. **경로를 하드코딩하면 깨짐** → 백엔드가 ZIP/폴더를 재귀 스캔해서 파일명 패턴(`followers_*`, `message_*`, `posts_*`)으로 찾는 방식이 필수.

---
