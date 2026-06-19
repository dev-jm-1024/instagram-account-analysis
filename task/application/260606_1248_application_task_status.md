# Application 계층 작업 현황 — 2026-06-06 세션

> 작성: 2026-06-06 12:48 · 범위: `src/main/java/com/instagram/analyze/application/**`
> 연관: api(media·reset 엔드포인트), frontend(탐색기 미리보기·데이터 삭제)

## 변경 상세
| 파일 | 변경 | 근거 |
|---|---|---|
| `explorer/ExplorerService(+Impl)` | `+mediaFile(path):Path` — 내용을 읽지 않고 게이트(requireCompleted)+루트내부 가드(zip-slip)만 적용한 디스크 경로 반환(컨트롤러가 스트리밍). file·media 공통 `resolveWithinRoot()` 추출. `tree()` 가 **미디어 포함**(기존 제외) | 미디어 미리보기 |
| `store/ImportWritePort` | `+reset()` — 인메모리 데이터 전부 비우고 IDLE. 디스크 무관 | 데이터 삭제 |
| `store/InMemoryImportStore` | `reset()` 구현 — 모든 컬렉션 `List.of()`·heatmap empty·importRoot null·scannedFiles `Map.of()`·importResult `IDLE_RESULT`(발행 마지막, replaceAll 과 동일 순서) | |
| `imports/ImportService(+RealImportService)` | `+reset()` — `writePort.reset()` + `running.set(false)` → IDLE 반환 | |
| `imports/StubImportService`(test) | `reset()` 구현(픽스처) | |

## 정책
- **삭제는 인메모리만** — 단일 사용자 로컬 앱이라 앱 재시작해도 어차피 비워지지만, 재시작 없이 즉시 초기화하려면 store reset 필요. 디스크 export 는 그대로라 재임포트로 복구.
- 미디어 엔드포인트는 file() 과 동일 가드 재사용 → 루트 외부/미존재는 G5(400/404).

## 테스트
- `ExplorerServiceTest`: `tree_includesMedia_andFileReadsContent`(미디어 포함으로 변경) + `mediaFile_returnsGuardedDiskPath_andRejectsTraversalAndMissing`(가드/탈출/미존재)
- reset 은 `ImportControllerTest`(MVC)로 end-to-end 검증

## 상태
✅ 구현·테스트 완료. 전체 88 tests green.
