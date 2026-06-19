# Instagram Analyzer : Domain Exception V1.0

---

## 0. 개요

이 문서는 `domain.md` 기반으로 발생 가능한 **모든 예외 상황을 그룹화**하고,
각 예외에 대한 HTTP 상태코드 · 예외코드(code) · 에러 메시지를 정의한다.

> 이 문서는 `ErrorResponse` 객체를 구현할 때 참고 문서로 활용된다.

---

## 1. ErrorResponse 객체 구조 (제안)

```json
{
  "code": "IMPORT_PATH_BLANK",
  "message": "폴더 경로를 입력해주세요.",
  "detail": "path must not be blank",
  "httpStatus": 400
}
```

| 필드         | 타입     | 설명                                    |
|--------------|----------|-----------------------------------------|
| `code`       | `String` | 예외 식별 코드 (프론트 분기 기준)       |
| `message`    | `String` | 사용자에게 보여줄 한국어 안내 메시지   |
| `detail`     | `String` | 개발·디버그용 상세 설명 (선택)          |
| `httpStatus` | `int`    | HTTP 상태코드 (중복 명시, 파싱 편의)    |

---

## 2. 예외 그룹 분류

| 그룹 | 분류명                              | 해당 도메인                           |
|------|-------------------------------------|---------------------------------------|
| G1   | 임포트 경로·포맷 검증               | 임포트 (1)                            |
| G2   | 본인 식별                           | 본인 식별 (1.4)                       |
| G3   | 임포트 미완료 상태에서 조회         | 히트맵 (5), 개요 대시보드 (9)         |
| G4   | 데이터 미존재 (빈 결과)             | 검색 기록 (6), 로그인 기록 (7), 각종 로그 (8) |
| G5   | 데이터 탐색기 경로 오류             | 데이터 탐색기 (10)                    |
| G6   | 파싱 중 항목 스킵 (내부 경고)       | 팔로우 (2), DM (3), 활동 (4), 각종 로그 (8) |

---

## 3. G1 — 임포트 경로·포맷 검증

> `POST /api/import` 에서 입력 경로 및 export 포맷을 검증할 때 발생하는 예외

| HTTP | code | message | detail |
|------|------|---------|--------|
| 400  | `IMPORT_PATH_BLANK` | 폴더 경로를 입력해주세요. | path must not be blank |
| 400  | `IMPORT_PATH_NOT_FOUND` | 입력한 경로가 존재하지 않습니다. | path does not exist on filesystem |
| 400  | `IMPORT_PATH_NOT_DIRECTORY` | 입력한 경로가 폴더가 아닙니다. | path is not a directory |
| 422  | `IMPORT_NOT_INSTAGRAM_EXPORT` | Instagram export 폴더가 아닙니다. JSON 포맷으로 재다운로드 후 시도해주세요. | no instagram export pattern found (followers_*.json, following.json, etc.) |
| 422  | `IMPORT_HTML_ONLY` | HTML 포맷은 지원하지 않습니다. Instagram에서 JSON 포맷으로 재다운로드해주세요. | only .html files found, no json files |

---

## 4. G2 — 본인 식별

> `POST /api/import/owner` 에서 수동 username 입력 시 발생하는 예외
>
> 자동 식별 실패(`ownerResolved: false`)는 HTTP 에러가 아닌 플래그로 처리되므로 별도 표기

| HTTP | code | message | detail |
|------|------|---------|--------|
| 400  | `OWNER_INPUT_BLANK` | 사용자 이름(username)을 입력해주세요. | owner username must not be blank |
| 409  | `DM_OWNER_NOT_RESOLVED` | 본인 식별이 필요합니다. 사용자 이름을 먼저 입력해주세요. | DM stats requested while owner unresolved (방어적 — 프론트가 사전 게이트하므로 정상 흐름엔 미도달) |

### 4.1 자동 식별 실패 플래그 (HTTP 에러 아님)

> 임포트는 `COMPLETED` 로 완료되지만 `ownerResolved: false` 플래그가 세워진다.
> 본인 식별이 필요한 메뉴(DM 통계 등)는 프론트에서 username 입력을 먼저 요구한다.

| 플래그 상태            | 설명                                               |
|------------------------|----------------------------------------------------|
| `ownerResolved: false` | 자동 탐색(personal_information.json · DM 교차분석) 으로 본인 식별 실패. 수동 입력 필요 |

---

## 5. G3 — 임포트 미완료 상태에서 조회

> 임포트가 완료되지 않은 상태(`IDLE`, `IN_PROGRESS`, `FAILED`)에서 메뉴 조회 시 발생

| HTTP | code | message | detail | 해당 도메인 |
|------|------|---------|--------|-------------|
| 503  | `IMPORT_NOT_COMPLETED` | 데이터 임포트를 먼저 진행해주세요. | import status is not COMPLETED | 히트맵 (5) |
| 200  | — | (HTTP 에러 아님) 빈 데이터 + `importRequired: true` 플래그 반환 | — | 개요 대시보드 (9) |

### 5.1 개요 대시보드 응답 (임포트 미완료)

```json
{
  "importRequired": true,
  "data": null
}
```

> 개요 대시보드는 HTTP 200 으로 임포트 유도 메시지를 내려주며 에러 응답이 아니다.

---

## 6. G4 — 데이터 미존재 (빈 결과 반환)

> export 에 해당 파일·디렉토리가 없을 경우. HTTP 에러 없이 **빈 결과 + 안내 메시지** 반환

| HTTP | code | message | detail | 해당 도메인 |
|------|------|---------|--------|-------------|
| 200  | `SEARCH_HISTORY_NOT_FOUND` | 검색 기록 데이터가 없습니다. | no search history files in export | 검색 기록 (6) |
| 200  | `LOGIN_HISTORY_NOT_FOUND` | 로그인 기록 데이터가 없습니다. | no login_activity*.json files in export | 로그인 기록 (7) |
| 200  | `MISC_LOG_DIR_NOT_FOUND` | 로그 데이터가 없습니다. | logged_information/ directory not found in export | 각종 로그 (8) |

> 세 경우 모두 HTTP 200 으로 빈 배열과 함께 위 code·message 를 내려준다.
> 프론트는 `code` 값으로 "데이터 없음" 안내 화면을 표시한다.

---

## 7. G5 — 데이터 탐색기 경로 오류

> `GET /api/explorer/file?path=...` 에서 경로 검증 실패 시 발생

| HTTP | code | message | detail |
|------|------|---------|--------|
| 400  | `EXPLORER_PATH_OUT_OF_ROOT` | 임포트 루트 폴더 외부 경로는 탐색할 수 없습니다. | requested path is outside import root |
| 404  | `EXPLORER_FILE_NOT_FOUND` | 요청한 파일을 찾을 수 없습니다. | file does not exist at requested path |
| 409  | `EXPLORER_NOT_IMPORTED` | 데이터 임포트를 먼저 진행해주세요. | import root not set (방어적 — requireCompleted 게이트로 통상 미도달) |

---

## 8. G6 — 파싱 중 항목 스킵 (내부 경고)

> HTTP API 레벨 에러가 아니라 **파싱 중 단일 항목을 스킵하고 경고를 누적**하는 내부 처리다.
> 전체 파싱이 중단되지 않으며, 정상 항목은 그대로 응답된다.
> 누적된 경고는 임포트 완료 응답 혹은 `GET /api/import/status` 에서 확인 가능하다.

| 내부 코드 | 설명 | 해당 도메인 |
|-----------|------|-------------|
| `PARSE_SCHEMA_MISMATCH` | 필수 키 누락 또는 예상 외 구조 → 해당 항목/대화방 스킵 | 팔로우 (2), DM (3) |
| `PARSE_TIMESTAMP_INVALID` | timestamp 가 null 이거나 0 이하 → 해당 항목 스킵 | 팔로우 (2), DM (3), 활동 (4), 로그인 (7), 검색 (6) |
| `PARSE_VALUE_BLANK` | username · sender_name 등 필수 문자열이 null/빈 문자열 → 해당 항목 스킵 | 팔로우 (2), DM (3) |
| `PARSE_STRING_LIST_EMPTY` | `string_list_data` 배열이 null 또는 빈 배열 → 해당 항목 스킵 | 팔로우 (2), 활동(좋아요·저장) (4) |
| `PARSE_JSON_ERROR` | JSON 형식 오류(MalformedJSON 등) → 해당 파일 스킵, "파싱 실패" 표시 | 각종 로그 (8) |
| `PARSE_FILE_EMPTY` | 파일 크기 0 → 해당 파일 스킵 | 파일 스캐너 (1.1) |
| `PARSE_STRING_DECODE_FAILED` | ISO-8859-1 → UTF-8 재디코딩 불가 → 원본 값 유지 | 문자열 정규화 (1.2) |

---

## 9. 전체 예외 코드 일람

| code | HTTP | 그룹 | 비고 |
|------|------|------|------|
| `IMPORT_PATH_BLANK` | 400 | G1 | |
| `IMPORT_PATH_NOT_FOUND` | 400 | G1 | |
| `IMPORT_PATH_NOT_DIRECTORY` | 400 | G1 | |
| `IMPORT_NOT_INSTAGRAM_EXPORT` | 422 | G1 | |
| `IMPORT_HTML_ONLY` | 422 | G1 | |
| `OWNER_INPUT_BLANK` | 400 | G2 | |
| `DM_OWNER_NOT_RESOLVED` | 409 | G2 | DM 통계, 방어적 |
| `IMPORT_NOT_COMPLETED` | 503 | G3 | 히트맵 전용 |
| `SEARCH_HISTORY_NOT_FOUND` | 200 | G4 | 빈 결과 |
| `LOGIN_HISTORY_NOT_FOUND` | 200 | G4 | 빈 결과 |
| `MISC_LOG_DIR_NOT_FOUND` | 200 | G4 | 빈 결과 |
| `EXPLORER_PATH_OUT_OF_ROOT` | 400 | G5 | |
| `EXPLORER_FILE_NOT_FOUND` | 404 | G5 | |
| `EXPLORER_NOT_IMPORTED` | 409 | G5 | 방어적 |
| `PARSE_SCHEMA_MISMATCH` | — | G6 | 내부 경고 |
| `PARSE_TIMESTAMP_INVALID` | — | G6 | 내부 경고 |
| `PARSE_VALUE_BLANK` | — | G6 | 내부 경고 |
| `PARSE_STRING_LIST_EMPTY` | — | G6 | 내부 경고 |
| `PARSE_JSON_ERROR` | — | G6 | 내부 경고 |
| `PARSE_FILE_EMPTY` | — | G6 | 내부 경고 |
| `PARSE_STRING_DECODE_FAILED` | — | G6 | 내부 경고 |

---

## 10. 설계 원칙 요약

1. **단일 사용자 · 로컬 앱** — 인증/권한(403) 에러는 없다. `EXPLORER_PATH_OUT_OF_ROOT` 는 보안이 아닌 실수 방지 목적이다.
2. **파싱 오류는 전체를 멈추지 않는다** — G6 예외는 항목을 스킵하고 경고를 누적한다.
3. **데이터 없음은 에러가 아니다** — G4 는 HTTP 200 + 빈 결과로 처리한다.
4. **임포트 미완료 상태는 도메인별로 다르게 처리한다**
   - 히트맵 → `503 IMPORT_NOT_COMPLETED`
   - 개요 대시보드 → `200 importRequired: true` (UX 안내 목적)
