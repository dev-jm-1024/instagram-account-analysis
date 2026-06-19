# Infrastructure 계층 작업 현황 — 2026-06-06 세션

> 작성: 2026-06-06 00:31 · 범위: `src/main/java/com/instagram/analyze/infrastructure/parse/**`, `src/main/resources/instagram-schema-mapping.yaml`
> 근거: 실데이터 1.9GB export(2026-06-03) 스키마 ↔ 파서 코드 정밀 대조 + 라이브 E2E 검증
> 연관 계층: config(ScanMappingProperties 규칙), api(조회 카운트 검증)

## 요약
좋아요·저장·피드 게시물이 **거의 안 불러와지던 적재 누락 3건**을 발견·수정했다. 근본 원인은
단일 — 파서가 **label_values 리스트형(최상위 `timestamp` + `label_values[].href`)** 스키마를 몰라서
`string_list_data`/`creation_timestamp` 가 없는 파일을 전량 스킵하고 있었다. 기존 79 테스트가
이를 못 잡은 이유는 like/saved 테스트 픽스처가 실데이터와 다른 합성 `string_list_data` 형식이었기 때문.

## 변경 상세
| 파일 | 변경 | 근거 |
|---|---|---|
| `parse/AbstractJsonParser` | +`labelValue(element,label)`(href/value 추출) · +`labelValuesUsername(element)`(username 류 라벨 우선, 없으면 마지막 비어있지 않은 value) | label_values 리스트형 |
| `parse/ActivityParser` | `addLike`/`addSaved`: string_list_data 없으면 → 최상위 `timestamp` + `labelValue(el,"URL")` 폴백. `addPost`: creation_timestamp → media[0] → **최상위 `timestamp`** 폴백. 파일명 분기에 `posts.json` 추가(`posts_` 접두 미매칭분) | liked_posts 636·saved 7·posts.json 141 |
| `parse/FollowParser` | string_list_data·title 없으면 → `labelValuesUsername()` + 최상위 `timestamp` 폴백 | close_friends 77·blocked label_values 폼 |
| `resources/instagram-schema-mapping.yaml` | exclude-path +`/ads_and_topics/`(ads `posts_viewed` 등 ACTIVITY 오분류 차단) · ACTIVITY name-equals +`posts.json` | 스캐너 오분류·누락 |

## 실데이터 검증 결과 (수정 전→후, /api 조회)
| 항목 | Before | After | export 문서 |
|---|---|---|---|
| like (liked_posts+comments) | 13 | **649** | 636+13 ✅ |
| saved | 0 | **7** | 7 ✅ |
| post (post+story+reels) | 1756 | **1897** | 141+7+1745+4 ✅ |
| comment | 359 | 359 | 336+23 ✅ |
| close_friend (CLOSE_FRIENDS) | 0 | **77** | 77 ✅ |
| import 잔여 경고 총계 | 78 | **1** | n=0 빈 파일 1건만 |
| ads 기인 TIMESTAMP_INVALID 경고 | ~1,592 | **0** | — |

> 라이브: POST /api/import(folderPath=2026-06-03 export) → COMPLETED, owner 자동확정.
> /api/overview·/api/activity?type=*·/api/follows?type=CLOSE_FRIENDS 로 카운트 확인. 히트맵 peak=수18시.

## 테스트
- `ActivityLoginSearchLogParserTest` +1: `activity_parsesLabelValuesListForm_topLevelTimestamp` — posts.json·liked_posts·saved_posts label_values 리스트형 적재(href 포함)
- `FollowParserTest` +1: `parsesLabelValuesForm_closeFriends_topLevelArrayAndLocalizedUsernameLabel` — close_friends label_values 폼 username/ts 추출
- `DefaultFileScannerTest` +1: `postsJson_classifiedActivity_butAdsPostsViewed_isUnknown` — posts.json→ACTIVITY, ads→UNKNOWN
- 전체 **82 tests green**(기존 79 + 신규 3)

## 상태
✅ 구현·회귀 테스트·실데이터 라이브 검증 완료(likes 649·saved 7·posts 1897·close_friends 77, 잔여 경고 1).
참고: `blocked_profiles.json`(5)도 동일 label_values 폼이라 파서는 처리 가능하나, 스캐너 FOLLOW 규칙·
`FollowRelationType.BLOCKED` 미정의로 현재 미노출(메뉴 요구 없음, 의도된 상태).
