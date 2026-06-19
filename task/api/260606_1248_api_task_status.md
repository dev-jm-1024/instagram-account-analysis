# API 계층 작업 현황 — 2026-06-06 세션

> 작성: 2026-06-06 12:48 · 범위: `src/main/java/com/instagram/analyze/api/**`
> 연관: application(ExplorerService.mediaFile·ImportService.reset), frontend(미디어 뷰어·시작분기·삭제)

## 추가/변경 엔드포인트
| 메서드 · 경로 | 응답 | 용도 |
|---|---|---|
| `GET /api/explorer/media?path=` | `ResponseEntity<Resource>`(바이트 스트리밍) | 탐색기 이미지/영상 미리보기. 확장자→Content-Type 추론(jpeg·png·webp·gif·heic·mp4·mov·webm), inline, 1h private 캐시 |
| `DELETE /api/import` | `ImportStatusResponse`(IDLE) | 데이터 삭제(초기화). 인메모리 store 비움 → 대시보드 잠김. 디스크 export 는 무관 |

## 변경 상세
| 파일 | 변경 |
|---|---|
| `explorer/ExplorerController` | `+media(path)` — `explorerService.mediaFile(path)`(가드된 디스크 경로) 를 `FileSystemResource` 로 스트리밍. `contentTypeOf(Path)` 확장자 매핑. heic 도 반환(브라우저 렌더 불가 시 프론트가 안내) |
| `imports/ImportController` | `+reset()` `@DeleteMapping` → `assembler.toStatus(importService.reset())` |

## 테스트
- `ImportControllerTest.reset_clearsImportedData_backToIdle`: import COMPLETED → DELETE → IDLE → status 도 IDLE
- 미디어 엔드포인트는 라이브 검증(curl: jpg image/jpeg 1440×1920·webp·mp4 200, 경로탈출 `../etc/passwd`→400) + 브라우저 DOM 프로브

## 상태
✅ 구현·테스트·실데이터/브라우저 검증 완료. 전체 88 tests green.
