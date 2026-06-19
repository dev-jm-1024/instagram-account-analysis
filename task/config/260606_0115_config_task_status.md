# Config 계층 작업 현황 — 2026-06-06 세션 (catch-all 노출)

> 작성: 2026-06-06 01:15 · 범위: `src/main/java/com/instagram/analyze/config/ScanMappingProperties.java`, `src/main/resources/instagram-schema-mapping.yaml`
> 목표: "모든 데이터가 다 보이게" — 전용 메뉴 없는 파일도 '각종 로그'에 노출
> 연관 계층: infrastructure(MiscLogParser catch-all 처리)

## 변경
| 파일 | 변경 |
|---|---|
| `ScanMappingProperties.classify()` | **최종 fallback `UNKNOWN` → `MISC_LOG`(catch-all)**. exclude-path 의미도 "숨김"→"전용 규칙만 건너뜀(오분류 방지) 후 범용 표로"로 변경. 이제 어떤 .json 도 UNKNOWN 으로 사라지지 않음 |
| `instagram-schema-mapping.yaml` | 평가-규칙 주석 갱신(③ 미매칭 → MISC_LOG). 규칙 자체는 불변 |

## 효과
- 전용 도메인(FOLLOW/MESSAGE/ACTIVITY/LOGIN/SEARCH/IMPORT)에 안 걸린 모든 파일이 MISC_LOG 로 분류 →
  기존 '각종 로그' 메뉴(LogsView misc_logs)에 **새 메뉴·엔드포인트 없이** 자동 노출.
- threads/·ads_and_topics/ 는 exclude-path 로 전용 분류는 계속 차단(오분류 방지)하되, 데이터는 MISC_LOG 로 노출.

## 검증(실데이터 라이브)
- 각종 로그: **59개 파일·7,697 레코드**(stories_viewed 1468·threads_viewed 1253·posts_viewed 1244·videos_watched 1240·story_likes 662·ads_viewed 570·profile_activity 334·archived_posts 134 …).
- 활동 무회귀: post 1897·like 649·comment 359·saved 7 (광고가 ACTIVITY 로 안 샘).
- import 경고 1건(n=0 빈 파일만).

## 상태
✅ 구현·테스트·실데이터 검증 완료. 전체 84 tests green.
