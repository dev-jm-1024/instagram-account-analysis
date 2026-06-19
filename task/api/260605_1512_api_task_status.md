# API 계층 작업 현황 — 2026-06-05 세션

> 작성: 2026-06-05 15:12 · 범위: `src/main/java/com/instagram/analyze/api/**`
> 근거 문서: `doc/260604_data_scan_plan_v1.md`, `doc/260605_frontend_integration_handoff.md`
> 연관 계층: application(서비스 구현), config(enum 컨버터·프로퍼티)

## 요약
data/ 자동 탐지·ZIP 업로드 엔드포인트를 ImportController 에 추가하고, 업로드 진행 중 import 거부를
위한 에러코드/핸들러를 보강했다. 기존 13개 → **엔드포인트 15개**.

## 변경 상세
| 파일 | 변경 | 근거 |
|---|---|---|
| `imports/ImportController` | +`GET /api/import/candidates`, +`POST /api/import/upload`(multipart), +`GET /api/import/upload/status`. 생성자에 `ExportDiscoveryService`·`UploadService` 주입 | data-scan plan §2.3, 업로드 흐름 |
| `imports/ImportAssembler` | +`toCandidates(...)`(→`CandidatesResponse`), +`toUpload(...)`(→`UploadStatusResponse`) | DTO 생성 단일 지점 유지 |
| `error/ErrorCode` | +`UPLOAD_IN_PROGRESS(409)` | A2 가드 |
| `error/GlobalExceptionHandler` | +`UploadInProgressException` → `UPLOAD_IN_PROGRESS` 매핑 | A2 |

## 신규/변경 엔드포인트
| 메서드 · 경로 | 응답 | envelope |
|---|---|---|
| `GET /api/import/candidates` | `CandidatesResponse{dataRoot, autoImportSingle, candidates[]}` | ❌ 직접 |
| `POST /api/import/upload`(multipart `file`) | `UploadStatusResponse{status,fileName,extractedEntries,targetPath,message}` | ❌ 직접 |
| `GET /api/import/upload/status` | `UploadStatusResponse` | ❌ 직접 |

> 임포트 그룹과 일관되게 envelope 없이 직접 반환. 조회 9종은 기존대로 `ApiResponse` envelope.

## 부수 효과(다른 계층 기인)
- 모든 `@RequestParam` enum(`?type=`)이 **대소문자 무시**로 바인딩됨 → `?type=post`==`POST`.
  컨버터(`IgnoreCaseEnumConverterFactory`)는 config 계층, 컨트롤러 코드 변경 없음.

## 테스트
- 컨트롤러 레벨 통합 테스트는 미추가(서비스·추출기 단위로 커버). 후속 선택: upload/candidates MVC 멀티파트 테스트.
- 실데이터 라이브 검증: 15개 엔드포인트 모두 정상 응답 확인(2026-06-05).

## 상태
✅ 구현·실데이터 검증 완료. 전체 73 tests green. 미결: upload/candidates MVC 통합 테스트(선택).
