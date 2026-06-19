# Infrastructure 계층 작업 현황 — 2026-06-06 세션 (후속: 최근언팔·범용파서)

> 작성: 2026-06-06 12:48 · 범위: `src/main/java/com/instagram/analyze/infrastructure/parse/**`
> 선행: `260606_0031_infrastructure_task_status.md`(label_values likes/saved/posts/close_friends)
> 연관: config(catch-all 분류), frontend(각종 로그 카드)

## 변경 상세
| 파일 | 변경 | 근거 |
|---|---|---|
| `parse/AbstractJsonParser` | `+readRoot(file, warnings)` — 루트를 통째로 트리로 읽음(배열/객체 무관). 범용 뷰어·소형 파일용 | catch-all·단일객체 |
| `parse/MiscLogParser` | `streamFirstArray` → `readRoot` 기반으로 **배열 / 배열-프로퍼티-객체 / 단일 객체** 모두 처리 + **label_values 평면화**(label→value/href). catch-all 전 파일을 표로 | 전 데이터 노출 |
| `parse/MiscLogParser` (인코딩) | **필드 키도 정규화** — string_map_data·최상위 키를 `normalizer.normalize()` 통과(값만 정규화하고 키는 raw 라 각종 로그 필드명이 mojibake 였음). `isTimeKey` 에 한국어 토큰(시각/시간/날짜/일시) 추가 | 한글 깨짐 |
| `parse/FollowParser` | `streamArray` → `readRoot` 기반 3형태: ①배열 ②rootKey 하위 배열 ③**단일 객체 레코드**. `recently_unfollowed_profiles.json` 은 배열·root-key 없이 label_values 단일객체라 통째로 누락되던 것 복구 | 최근 언팔 |

## 실데이터 검증 (최근 언팔)
| 항목 | Before | After |
|---|---|---|
| recently_unfollowed (UNFOLLOWED) | 0 (SCHEMA_MISMATCH 드롭) | **1 (test_user2)** |
| import 잔여 경고 | 1 | 0 |

> `recently_unfollowed_profiles.json` 실제 형태: `{timestamp, media:[], label_values:[{URL},{이름},{사용자 이름}], fbid}` — 단일 객체. username 은 `사용자 이름` 라벨 value(`labelValuesUsername` 재사용).
> `followArray()` 는 rootKey 만 신뢰(빈 `media:[]` 오인 방지, 첫배열 폴백 안 함).

## 테스트
- `FollowParserTest.parsesSingleObjectRoot_recentlyUnfollowed_labelValuesForm`
- `ActivityLoginSearchLogParserTest.miscLog_catchAll_flattensLabelValues_andHandlesObjectOnlyFile`
- `ActivityLoginSearchLogParserTest.miscLog_normalizesMojibakeFieldKeys` — 키 mojibake("변경됨") 복구

## 상태
✅ 구현·테스트·실데이터 검증 완료. 전체 89 tests green.
> 한글 인코딩 라이브 재검증: 각종 로그 7,697행 전수 스캔 → mojibake 키·값 **0건**. profile_changes 키 `변경됨`·`새 값` 정상.
