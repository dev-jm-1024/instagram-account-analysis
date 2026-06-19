# Frontend 계층 작업 현황 — 2026-06-07 세션 (각종 로그 미디어 인식 카드)

> 작성: 2026-06-07 01:06 · 범위: `frontend/src/features/logs/{rows.ts,MiscLogDetail.tsx}`
> 선행: BE 파서가 media 중첩에서 uri·캡션·시각 복구(`task/infrastructure/260607_0106_*`)
> 연관: 탐색기 `/api/explorer/media` 엔드포인트 재사용

## 변경
| 파일 | 변경 |
|---|---|
| `rows.ts` | `+mediaKind(path)`(확장자→image/video/heic, 탐색기와 동일 규약) · `+mediaUrl(path)`(`/api/explorer/media?path=`) · `+rowMedia(row)`(미디어 확장자 가진 첫 필드 탐지, http 링크 제외) |
| `MiscLogDetail.tsx` | 행을 `{row, ts}`로 zip(BE가 분리 노출한 `timestamps[]` 정렬 사용) → `MiscRow(row, ts)`. **시각은 ts 우선**(필드에 시각 없어도 표시). **미디어 경로 감지 시 썸네일**(`MediaThumb`): 이미지 인라인(`<img loading=lazy>`, 실패 시 아이콘 폴백), 동영상·heic는 아이콘+새 탭 열기. 미디어 필드는 나머지 목록에서 제외. 빈 행은 '내용 없음' |

## 효과
- 보관 게시물이 **썸네일 + 캡션(접기/펼치기) + 날짜** 카드로 보임(기존 빈 카드 → 의미 있는 콘텐츠 카드).
- 프로필 사진·기타 콘텐츠 등 미디어 가진 행도 동일하게 썸네일.
- 단일 사용자 로컬: 썸네일은 `/api/explorer/media` 스트리밍(가드된 경로), 페이지당 50행만 + `loading=lazy`.

## 검증
- `npx tsc -b` ✅ · ESLint(logs) 0 ✅ · `npm run build`(vite) ✅
- 라이브 브라우저 확인은 후속(앱 미기동). uri 경로 실존은 데이터로 확인(`media/posts/...jpg`).

## 후속(선택)
- 보관 게시물을 '기타' 내 별도 갤러리 그리드로 승격 / 동영상 인라인 썸네일(poster).
