# API/도메인 DTO 작업 현황 — 2026-06-06 세션 (각종 로그 row timestamp 노출)

> 작성: 2026-06-06 15:06 · 범위: `api/log/MiscLogAssembler`, `domain/log/dto/MiscLogResponse`
> 연관: frontend(각종 로그 인사이트 재설계 — 월별 추세·통합 타임라인이 row별 시각을 요구)

## 배경
프론트 '각종 로그'의 조회 이력 집계(월별 추세·타임라인)에는 row별 시각이 필요하다. 그러나
어셈블러가 `LogRecord.getFields()`만 내보내고 **이미 파싱·정규화한 `LogRecord.timestamp`는 버려서**,
`string_map_data` 폼(예: posts_viewed 의 `Author`/`Time`)은 시각이 fields 에 안 실려 프론트가 시각을 못 구했다.

## 변경
| 파일 | 변경 |
|---|---|
| `domain/log/dto/MiscLogResponse.LogFileView` | `+List<Long> timestamps` — rows 와 같은 순서·길이로 정렬된 정규화 epoch ms. 시각 미인식 행은 **null**(그래서 `List.copyOf` 대신 `unmodifiableList(new ArrayList<>())` 로 null 원소 허용). 생성자 3-arg 로 변경 |
| `api/log/MiscLogAssembler` | rows 와 함께 `getRecords().stream().map(r -> r.getTimestamp()==null?null:r.getTimestamp().getValue())` 로 timestamps 병렬 생성 |

> 비파괴적 추가(JSON 필드 1개). 호출부는 어셈블러 단일 지점, 다른 LogFileView 생성자 사용처 없음.
> TS: `LogFileView.timestamps?: (number|null)[]` 추가.

## 검증
- `./gradlew compileJava` ✅ · 전체 테스트 **87 passed / 0 failed / 0 skipped**(회귀 없음, ReadControllersTest·파서 테스트 포함).
