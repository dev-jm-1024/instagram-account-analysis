# Frontend 계층 작업 현황 — 2026-06-06 세션 (카드 UI·시작분기·삭제/갱신)

> 작성: 2026-06-06 12:48 · 범위: `frontend/src/**` (활성 트리는 `frontend/`, `instaarchive-insights/`는 구사본)
> 선행: `260606_0129`(misc UX)·`260606_0220`(미디어 미리보기)
> 연관: api(media·DELETE reset), application(reset)

## 변경 상세
| 파일 | 변경 |
|---|---|
| `features/explorer/RawExplorer.tsx` | 확장자로 미디어 판별 → `<img>`/`<video>`(상대경로 `/api/explorer/media`), heic/로드실패는 안내+"원본 열기↗". 트리 파일 아이콘(이미지/영상). **레이아웃 버그 수정**: 큰 폴더 펼치면 그리드 행이 늘어 미리보기 안 보이던 것 → `md:grid-rows-1`+`min-h-0` |
| `features/logs/LogsView.tsx` | 각종 로그 raw 키-값 덤프 → **카드**(`MiscRow`/`classifyRow`): 시각(포맷)·인스타 링크·캡션 2줄 클램프+클릭 펼치기, 나머지 필드 작게. (마스터-디테일·페이지네이션은 선행) |
| `features/onboarding/MainScreen.tsx` | 시작 버튼 분기 — 임포트됨(`importStatus===COMPLETED`)→`/dashboard`("대시보드로 이동"), 아니면→`/upload` |
| `features/onboarding/UploadScreen.tsx` | data/ 후보 표시 — 1개면 자동 임포트(effect+ref 가드), 여러 개면 선택 버튼. 0개면 ZIP 업로드만 |
| `features/uploader/Uploader.tsx` | "현재 불러온 데이터" 패널 + **데이터 삭제** 버튼(window.confirm → `api.resetImport()` → `qc.clear()`+`navigate('/main')` 대시보드 잠김). 갱신=기존 후보 재임포트 |
| `services/api/client.ts`·`endpoints.ts` | `+delJson`(DELETE) + `api.resetImport()` |
| `services/api/hooks/useImportFlow.ts` | **버그 수정**: importStatus 공유 캐시(이미 COMPLETED)를 phase 로 오판 → Uploader 진입 즉시 '완료'로 보고 overview 로 튕김. `startMutation.isSuccess` 일 때만 import 상태 반영 |

## 검증
- tsc strict · ESLint 0 · vite build OK
- 브라우저 E2E(일회성): `시작하기→/upload(후보3)→임포트→/dashboard→데이터삭제(confirm)→/main`, **콘솔 에러 0**. 미디어 jpg/webp 로드·패널맞춤·mp4 재생·heic 안내 DOM 프로브 확인(사진 내용은 프라이버시상 미열람)

## 후속(선택)
- 미디어 ②인라인 썸네일(활동/게시물)·③heic 변환 · 각종로그 백엔드 지연로딩(페이로드 20–30MB+)

## 상태
✅ 구현·검증 완료.
