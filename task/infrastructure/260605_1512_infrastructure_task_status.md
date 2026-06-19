# Infrastructure 계층 작업 현황 — 2026-06-05 세션

> 작성: 2026-06-05 15:12 · 범위: `src/main/java/com/instagram/analyze/infrastructure/**`
> 근거: 실데이터 1.9GB export E2E 검증(2026-06-05), `doc/domain.md` 부록 B
> 연관 계층: application(RealUploadService 가 ZipExtractor 사용), config(ScanMappingProperties)

## 요약
ZIP 압축 해제(zip-slip 방어), 스캐너 분류의 YAML 외부화, 그리고 **실제 export 스키마가
domain.md 가정과 달라 발생한 파서 버그 4건**(실데이터 검증으로 발견)을 수정했다.

## 변경 상세
| 파일 | 변경 | 근거 |
|---|---|---|
| `archive/ZipExtractor`(interface) + `DefaultZipExtractor`(신규) | `ZipInputStream` 스트리밍 전체 추출(미디어 포함) + **zip-slip 방어**(엔트리가 대상 디렉토리 안쪽인지 검사), 8KB 버퍼 | 업로드 흐름 |
| `scan/DefaultFileScanner` | 하드코딩 classify 제거 → `ScanMappingProperties` 주입받아 위임. threads 제외·검색패턴 확장은 이제 **YAML 규칙** | B1 |
| `parse/FollowParser` | **A**: `string_list_data[].value` 없으면 `title` 로 username 폴백(+null string_list_data graceful) | 실데이터 A |
| `parse/SearchParser` | **B**: 검색어가 value/map/list 에 없으면 `title` 폴백(profile_searches) | 실데이터 B |
| `parse/LoginParser` | **D**: timestamp 가 `title`의 ISO-8601 이면 epoch 변환 + `string_map_data` 값에서 IPv4 정규식 탐색(지역화·mojibake 키 대응) | 실데이터 D |

> 스캐너의 검색 파일명 확장(`*_searches.json`·`/recent_searches/`)과 threads 제외는 코드가 아니라
> `instagram-schema-mapping.yaml` 에 표현됨(config 계층 참조).

## 실데이터 검증 결과(수정 전→후)
| 항목 | Before | After |
|---|---|---|
| following(A) | 0 | 390 (맞팔 126) |
| search(B) | NOT_FOUND | 9 ("모란카페" mojibake 복원) |
| logins(D) | [] | 105 (IP 59.11.98.44) |
| threads 경고 | 다발 | 0 |

## 테스트
- `DefaultZipExtractorTest`(2): 전체 추출(미디어 포함) / zip-slip 차단
- `DefaultFileScannerTest` +2: recent_searches 분류 / threads UNKNOWN (실제 YAML 매핑 로드)
- `FollowParserTest` +1: title-기반 username
- `ActivityLoginSearchLogParserTest` +2: login ISO title+IP / search title
- (C=enum 컨버터는 config 계층 테스트)

## 상태
✅ 구현·회귀 테스트·실데이터 라이브 검증 완료. 전체 73 tests green.
참고: zip-bomb 한도 없음(단일 사용자라 노트만). FollowParser 등의 root-key 매핑은 아직 코드 — 파서 레벨 YAML 외부화는 후속(B1b 후보).
