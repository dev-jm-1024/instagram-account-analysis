# Config 계층 작업 현황 — 2026-06-05 세션

> 작성: 2026-06-05 15:12 · 범위: `src/main/java/com/instagram/analyze/config/**`, `src/main/resources/*.yaml`
> 근거 문서: `doc/260604_data_scan_plan_v1.md`, `doc/domain.md` 부록 B
> 연관 계층: infrastructure(DefaultFileScanner 가 ScanMappingProperties 사용), api(enum 컨버터)

## 요약
배포용 `data/` 설정, 대용량 업로드 멀티파트 설정, enum 대소문자 무시 컨버터, 그리고 **파일→도메인
분류 규칙의 YAML 외부화**(domain.md 부록 B 목표)를 추가했다.

## 변경 상세
| 파일 | 변경 | 근거 |
|---|---|---|
| `InstagramProperties` | +`Data` 중첩(`root=./data`, `autoImportSingle`, `maxScanDepth=4`) | plan §2.1 |
| `ScanMappingProperties`(신규) | `@ConfigurationProperties("instagram.scan")` — 파일→도메인 분류 규칙 바인딩 + `classify()` + `fromClasspathYaml()`(비-Spring 테스트용 정적 로더) | B1 |
| `IgnoreCaseEnumConverterFactory`(신규) | 쿼리 enum 대소문자 무시(`?type=post`==`POST`). 알 수 없는 값은 400 유지 | 실데이터 C |
| `WebConfig` | +`addFormatters` 에 위 컨버터 등록 | 실데이터 C |
| `resources/instagram-schema-mapping.yaml`(신규) | 분류 매핑 본체(exclude-path + 도메인별 매처). **스키마 변화 시 여기만 수정** | B1, domain.md 부록 B |
| `resources/application.yaml` | +`instagram.data.*`, +`spring.config.import`(매핑 머지), +멀티파트 `max-file/request-size=-1`·`file-size-threshold=2KB`, +`server.tomcat.max-swallow-size=-1` | data/·업로드·B1 |

## 매핑 규약(instagram-schema-mapping.yaml)
- `exclude-path-contains`(예: `/threads/`) 걸리면 UNKNOWN
- `rules` 순서 평가, 규칙 내 조건 OR. 매처: `name-equals`·`name-starts-with`·`name-ends-with`·`name-contains-all`·`path-contains`. 매칭 없으면 UNKNOWN
- 검색이 logged_information 보다 먼저 claim 하도록 규칙 순서로 보장

## 테스트
- `IgnoreCaseEnumConverterFactoryTest`(2): 소문자·trim 변환 / 미지값 예외
- `ScanMappingProperties.fromClasspathYaml()` 는 `DefaultFileScannerTest`·`ImportPipelineTest`·`RealImportServiceTest` 가 사용(운영과 동일 매핑으로 검증 = 단일 진실)
- @SpringBootTest 컨텍스트 로딩으로 `spring.config.import`+바인딩 동작 확인

## 상태
✅ 구현·테스트·실데이터 검증 완료(외부화 후 분류 결과 불변: following 390·search 9·logins 105).
미결: C1 SPA forwarding, C2 dev/배포 프로파일 분리(data.root), C3 단일 JAR(프론트 번들 동봉) — 프론트 진척 후.
