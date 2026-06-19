# Frontend 계층 작업 현황 — 2026-06-06 세션 (로그 분석기 디자인 폴리시 · 컴포넌트 분해)

> 작성: 2026-06-06 15:27 · 범위: `frontend/src/features/logs/**`
> 선행: `260606_1506`(각종 로그 인사이트 재설계 — 기능). 이번은 **디자인/구조만**(기능 무변경).
> 사용자 요청: ①레이아웃 찌그러짐 정리 ②이모지 → React 공식(lucide) 아이콘 ③한 파일 과적 → 컴포넌트 분해·재사용

## 1. 단일 파일 분해 (967줄 LogsView.tsx → 11개 파일)
| 파일 | 책임 | 줄 |
|---|---|---|
| `LogsView.tsx` | 셸: ViewHeader + 서브탭(PillTabs) + 섹션 스위치 | 64 |
| `LoginsSection.tsx` | 로그인 세션(G-5) + LoginRow | 99 |
| `SearchesSection.tsx` | 검색 기록(G-4) | 46 |
| `MiscLogsSection.tsx` | 각종 로그 오케스트레이터 + CategoryTabBar | 111 |
| `ViewingHistoryDashboard.tsx` | 👀 조회 이력 집계 + ViewingRow | 119 |
| `AdTrackingPanel.tsx` | 📢 광고·추적 요약+드릴다운 | 29 |
| `FilePickerPanel.tsx` | 마스터-디테일 파일 피커 | 70 |
| `MiscLogDetail.tsx` | 선택 파일 행 카드 + MiscRow | 132 |
| `classify.ts` | 카테고리 분류(로직, JSX 없음): `MiscCategory`·`CATEGORY_ORDER`·`FILE_RULES`·`classifyFile`·`categorize`·`groupByCategory`·`CatFile` | 98 |
| `rows.ts` | 행 헬퍼(로직): `classifyRow`·`prettyValue`·`rowEntity`·`rowUrl`·`mergeRows`·`bucketMonthly`·`topEntities`·`monthKey`·정규식 | 131 |
| `components.tsx` | **공용 프리미티브**: `SectionPanel`·`MetricChip`·`RankBarList`·`MiniBarChart`·`Pagination` | 191 |

> 로직(`classify`/`rows`)과 표현(컴포넌트)을 분리. 프리미티브는 로그인·검색·조회이력이 공유(예: `RankBarList`=검색 Top10 + 작성자 Top10, `Pagination`=타임라인 + 파일 드릴다운).

## 2. 디자인 폴리시 ('찌그러짐' 해소)
- **표면 통일**: ad-hoc glass `<div>` → 공용 `GlassPanel` 기반 `SectionPanel`(헤더+본문 규약)로 전 섹션 통일. border/blur/highlight 일관.
- **순위 막대 정렬**: 작성자 Top10 막대가 고정 px 너비라 어긋나던 것 → `RankBarList`가 **고정 트랙(w-20) + 비율 채움**, 카운트 `tabular-nums` 우정렬로 가지런하게.
- **카테고리 탭바**: `ml-auto`로 끼워 넣어 줄바꿈 시 깨지던 '총 N개' 캡션 → 별도 영역(flex-between)으로 분리. 탭은 flex-wrap.
- **수치 칩 균일화**: 들쭉날쭉하던 카운트 칩 → `MetricChip`(min-w·tabular-nums)로 폭 통일(로그인 요약·조회 소스·광고 요약 공통).
- 로딩 상태: 커스텀 `Center` 제거 → 공용 `EmptyState`(spin)로 통일(ActivityView와 동일 패턴).

## 3. 이모지 → lucide 아이콘
- 상단 서브탭: `🔐/🔍/📋` → `KeyRound`/`Search`/`FileStack`(PillTabs label=ReactNode 활용, `TabLabel` 헬퍼).
- 카테고리 탭: `👀📢❤️🔒🧩` → `Eye`/`Megaphone`/`Heart`/`Lock`/`Boxes`(`CATEGORY_ORDER`에 `icon` 필드).
- 카피 정리: "검색 history"→"검색 기록", 영문 "Sessions Timeline/Keyword Frequencies"→한글.

## 검증
- `npx tsc -b` ✅ · ESLint(logs feature) 0 ✅ · `npm run build`(vite) ✅
- 기능 무변경(라우팅 `tabRegistry`는 default export + `subMode` prop 그대로). 라이브 브라우저 확인은 후속(앱 미기동).
