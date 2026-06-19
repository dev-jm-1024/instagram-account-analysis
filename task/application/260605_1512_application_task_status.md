# Application 계층 작업 현황 — 2026-06-05 세션

> 작성: 2026-06-05 15:12 · 범위: `src/main/java/com/instagram/analyze/application/**`
> 근거 문서: `doc/260604_data_scan_plan_v1.md`, `doc/260605_frontend_integration_handoff.md`
> 연관 계층: api(엔드포인트), infrastructure(ZipExtractor), config(InstagramProperties.Data)

## 요약
배포 컨벤션(jar 옆 `data/`)을 위해 **export 자동 탐지**와 **ZIP 업로드→추출** 진입 보조를 추가했다.
임포트 코어(ETL 파이프라인)는 손대지 않고, 탐지·업로드를 앞단에만 얹어 기존
`POST /api/import {folderPath}` 로 합류시킨다.

## 변경 상세
| 파일 | 변경 | 근거 |
|---|---|---|
| `imports/ExportCandidate`(record) | data/ 후보 DTO: path·name·account·exportedAt | plan §2.2 |
| `imports/ExportDiscoveryService` | data/ 직속 하위를 얕게 스캔(marker 파일)해 export 후보 수집. throw 안 함(미존재=빈목록), 날짜 내림차순 정렬 | plan §2.2 |
| `imports/UploadService`(interface) | 업로드 본문 저장 + (ZIP)비동기 추출 진입점 | 업로드 흐름 |
| `imports/RealUploadService` | 멀티파트 스트리밍 저장(`Files.copy`, 메모리 평탄) → ZIP `data/<basename>/` 비동기 추출(`importExecutor` 재사용), 원본 ZIP 보관, **재업로드 시 targetDir 재귀삭제(orphan 정리, data/ 직속 가드)**, 파일명 sanitize | 업로드 흐름·리뷰 |
| `imports/UploadState`(record) | 업로드/추출 상태 스냅샷(volatile 발행) | |
| `imports/UploadInProgressException` | 업로드 SAVING/EXTRACTING 중 import → 409 매핑용 | A2 |
| `imports/RealImportService` | **+`UploadService` 주입 + import 진입 시 SAVING/EXTRACTING 거부 가드(A2)**. 생성자 시그니처 변경 | A2 리뷰 |

> store/support 등 기존 포트·게이트는 변경 없음. 단일 스레드 `importExecutor` 를 추출·ETL 이 공유(단일 사용자라 시점 비중첩).

## 흐름
```
업로드: POST /upload → SAVING(저장) → EXTRACTING(비동기) → COMPLETED  (status 폴링)
탐지:   GET /candidates → 0/1/N 분기
임포트: POST /api/import {folderPath} → (A2) 추출 중이면 거부 → 기존 ETL
```

## 테스트
- `ExportDiscoveryServiceTest`(5): marker 탐지·이름파싱·날짜정렬·비-export 제외·data 미존재 빈목록
- `RealUploadServiceTest`(5): ZIP 저장+추출·비-ZIP·blank·파일명 경로탈출 차단·**재업로드 orphan 정리**
- `RealImportServiceTest` +1: EXTRACTING 중 import 거부(`UploadInProgressException`) + 생성자(FakeUploadService) 갱신

## 상태
✅ 구현·테스트·실데이터 검증 완료. 전체 73 tests green.
미결: A3 data/ retention(오래된 export·ZIP 정리), B2 빈 대화방 필터, B3 owner-mismatch 휴리스틱.
