# 미디어 미리보기 (탐색기) — 2026-06-06 세션

> 작성: 2026-06-06 02:20 · 범위: api(ExplorerController)·application(ExplorerService/Impl)·frontend(RawExplorer)
> 목표 ①: 탐색기에서 이미지/영상 미리보기(jpg/webp/png/gif/mp4/mov). heic 는 안내.

## 변경
| 계층 | 파일 | 변경 |
|---|---|---|
| application | `ExplorerService(+Impl)` | `+mediaFile(path):Path` — 게이트+루트내부 가드(zip-slip) 공통 `resolveWithinRoot()` 추출. **tree() 가 미디어 포함**(기존 제외 → 포함) |
| api | `ExplorerController` | `+GET /api/explorer/media?path=` → `FileSystemResource` 스트리밍, 확장자→Content-Type, inline, 1h private 캐시 |
| frontend | `RawExplorer.tsx` | 확장자로 미디어 판별 → `<img>`/`<video>`(상대경로 `/api/explorer/media`), heic/로드실패는 안내+"원본 열기↗". 미디어는 텍스트 fetch 안 함. 트리 파일 아이콘(이미지/영상) |
| frontend | 〃 | **레이아웃 버그 수정**: 큰 폴더 펼치면 트리가 그리드 행을 늘려(5032px) 미리보기가 안 보이던 것 → `md:grid-rows-1`(행 minmax(0,1fr) 고정) + 스크롤 영역 `min-h-0` |

## 검증 (Playwright, 일회성 — 사진 내용은 미열람)
- 백엔드(curl): jpg 200·image/jpeg(1440×1920)·webp 200·image/webp·mp4 200·video/mp4 · **경로탈출 400 차단** · tree 에 media/posts(jpg114·webp66·heic9·mp4 2) 포함.
- 브라우저(DOM 프로브): jpg/webp 로드 완료(naturalWidth>0)·**패널(517px)에 맞게 표시(364×485)** · mp4 readyState4·controls · heic 안내(ImageOff+문구+원본열기 링크, img 없음) · **콘솔 에러 0**.
- heic 안내 화면 스크린샷으로 레이아웃 확인(사진 없는 UI). 실제 사진은 프라이버시상 미열람, 객관 측정만.
- 백엔드 86 tests green(`ExplorerServiceTest`: 미디어 포함·mediaFile 가드/탈출/미존재 추가). 프론트 tsc·eslint·build OK.

## 미포함(설계상)
- **HEIC(112장) 실제 렌더**: 브라우저 미지원 → 서버 변환 필요(외부 바이너리/네이티브), 포터블 무외부의존 목표와 충돌 → 안내+원본열기로 대체.
- 활동/게시물 인라인 썸네일(②)·동영상 range 요청(seek)·heic 변환(③)은 별도.
