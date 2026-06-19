# Frontend 계층 작업 현황 — 2026-06-06 세션 (각종 로그 인사이트 재설계)

> 작성: 2026-06-06 15:06 · 범위: `frontend/src/features/logs/LogsView.tsx`
> 연관: api(MiscLogAssembler 가 row 별 timestamp 노출), domain.dto(MiscLogResponse.LogFileView)
> 배경: catch-all '각종 로그'(59파일·7,697행)가 영어 파일명 평면 피커라 UX 불량. 사용자 합의로 **인사이트 우선 + 로그 탭 내부 해결**(새 사이드바 메뉴 0).

## 문제
- 전용 메뉴 없는 모든 파일이 한 곳에 — 의미가 5종(조회/광고추적/스토리상호작용/계정보안/기타)으로 뚜렷이 갈리는데 평평하게 나열.
- 영어 snake_case 파일명을 사용자가 직접 해독. 70%(≈5,327행)를 차지하는 '조회 이력'이 안 쓰는 설정 파일과 동급.
- 행 렌더가 범용 휴리스틱(time/url/text)뿐 — 집계·인사이트 0. 개요/활동 등 다른 메뉴의 '인사이트' 톤과 불일치.

## 변경 (MiscLogsSection 재작성 — 카테고리 서브탭 오케스트레이터)
| 구성 | 내용 |
|---|---|
| 카테고리 분류 | `FILE_RULES`(basename 정규식 → 카테고리 + **친화 한글 라벨**) + `classifyFile()`. 미매치는 `other` + basename 정리. 데이터 있는 카테고리만 서브탭 노출, 기본=행 수 최다(보통 조회 이력) |
| 👀 조회 이력 | `ViewingHistoryDashboard` — 카테고리 전 파일 행을 `mergeRows`로 합쳐 ①소스별 카운트 칩 ②**작성자 Top 10**(`rowEntity`=시각·URL 아닌 첫 값) ③**월별 추세**(`MiniMonthlyBars`, ts→YYYY-MM) ④**통합 타임라인**(소스 필터 select + 50행 페이지네이션, ts 내림차순) |
| 📢 광고·추적 | `AdTrackingPanel` — 프라이버시 안내 배너 + 파일별 카운트 요약 카드 그리드 + `FilePickerPanel` 드릴다운 |
| ❤️🔒🧩 | `FilePickerPanel`(기존 마스터-디테일 추출·재사용) — 파일 리스트에 친화 라벨, `MiscLogDetail`/`MiscRow` 그대로 |
| 시각 데이터 | row별 시각은 백엔드가 새로 노출한 `LogFileView.timestamps[i]` 사용(string_map_data 폼은 fields에 시각이 없어 프론트 추출 불가했음). 미인식 행은 '시각 없음'·월별 집계 제외(graceful) |

## 성능/설계 (단일 사용자, 서버 왕복 0)
- `/api/logs` 1회 페치 → 인메모리. 카테고리 분류·머지·집계 전부 `useMemo`. 타임라인은 페이지당 50행만 렌더.
- 페이지 리셋은 effect 대신 렌더 중 보정(기존 패턴 유지).

## 검증
- `npx tsc -b` ✅ · ESLint(LogsView) 0 ✅ · `npm run build`(vite) ✅
- 라이브 브라우저 E2E 미수행(앱 미기동) — 후속에서 실데이터로 카운트 일치·작성자 Top·월별·타임라인 확인 필요.

## 후속(선택)
- 실데이터 라이브 검증(작성자 라벨이 파일별로 일관적인지, ts 누락 비율).
- 광고·추적 카테고리도 '관심 없음 vs 본 광고' 같은 의미 집계 추가 여지.
