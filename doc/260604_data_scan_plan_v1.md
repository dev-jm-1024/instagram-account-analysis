# data/ 자동 스캔 · export 탐지 설계 계획 v1

> 작성: 2026-06-04
> 대상: 배포 시 `data/` 폴더 자동 스캔 → Instagram export 디렉토리 탐지 → (단일) 자동 임포트 / (다중) 사용자 선택 / (수동) 경로 override
> 상태: **백엔드 탐지 레이어 구현 완료 (2026-06-05).** 아래 §7 구현 현황 참조. 프론트 연동은 미착수.

---

## 7. 구현 현황 (2026-06-05) — 백엔드 청크 1 완료

✅ 구현·테스트 통과(전체 58 tests green, discovery 5건):
- `config/InstagramProperties.Data` (root/autoImportSingle/maxScanDepth) + `application.yaml` `instagram.data.*`
- `application/imports/ExportCandidate`(record), `ExportDiscoveryService`(얕은 marker 탐지, throw 안 함)
- `GET /api/import/candidates` → `ImportController` + `ImportAssembler.toCandidates`
- `ExportDiscoveryServiceTest`(marker 탐지·이름파싱·날짜 내림차순 정렬·비-export 제외·data 미존재 빈목록)

**계획 대비 변경점(의도적)**:
1. `maxScanDepth` 기본 **2 → 4**. 표준 export 의 marker 는 깊이 3(connections/followers_and_following/following.json), inbox message_*.json 은 깊이 4. depth 2 면 marker 를 놓침. media/ 깊이 폭주는 `Files.find` + 첫 marker 단락으로 무관.
2. `CandidatesResponse` 위치 = **`domain/imports/dto`** (api/imports/dto 아님). 기존 import DTO(ImportStatusResponse 등)가 거기 있어 일관성 우선.
3. `approxJsonCount` 필드 **드롭**. 정확 산출엔 walk 비용 → v1 제외. 대신 응답에 `autoImportSingle` 정책 힌트 추가.
4. `candidates` 응답 = envelope 없이 **직접 반환**(import 그룹과 일관, §2.3 권장안 채택).

**여전히 미구현(다음 청크)**:
- 프론트 연동: Uploader 가 `GET /candidates` 호출 → 0/1/N 분기 UX (G-1 재설계와 묶임)
- §5 미결: zip-only 자동 압축해제, dev(`../data`)/배포(`./data`) 프로파일 분리, 재임포트 덮어쓰기 확인 UX

---

---

## 0. 배경 — 배포 컨벤션

export 데이터는 **항상 jar 옆 `data/` 폴더**에 둔다. 배포 레이아웃:

```
deploy/
├── instagram-analyze.jar
├── data/
│   ├── instagram-{account}-{date}-{hash}.zip      (원본 zip, optional)
│   └── instagram-{account}-{date}-{hash}/         (압축 해제된 export 디렉토리 = 실제 ETL 대상)
└── jdk/
```

- 개발 중 위치: `<project-root>/../data/` → `/Users/dev-jm/workspace/dev/instragram-analyze/data/`
- 현재 실제 샘플 1건 존재:
  - `data/instagram-myaccount-2026-06-03-igilBpS1.zip`
  - `data/instagram-myaccount-2026-06-03-igilBpS1/` ← 2025~26 중첩 구조
    (`connections/`, `your_instagram_activity/`, `personal_information/`,
     `security_and_login_information/`, `logged_information/`, `ads_information/`,
     `apps_and_websites_off_of_instagram/`, `preferences/`, `media/`)

## 0.1 결정 사항 (2026-06-04)

| 항목 | 결정 |
|------|------|
| export 폴더 지정 | **둘 다** — 기본은 `data/` 자동 스캔, 사용자가 원하면 수동 경로 override |
| 다중 export 발견 시 | **사용자 선택** — 후보 목록을 보여주고 1개 고름 (자동 선택 안 함) |
| `data/` 루트 경로 | 외부화 설정값(`instagram.data.root`)으로 관리 |

---

## 1. 현재 상태와 갭

**현재 백엔드(`application/imports`, `infrastructure/scan`)**
- `POST /api/import { folderPath }` — 사용자가 **단일 export 폴더 경로를 직접 입력**해야만 함.
- `ImportValidator.validatePath(root)` — 존재·디렉토리 검증(G1: PATH_NOT_FOUND / PATH_NOT_DIRECTORY).
- `ImportValidator.validateExport(scanned, root)` — 스캔 결과에 known 도메인(FOLLOW/MESSAGE/ACTIVITY/LOGIN) JSON 이 하나라도 있으면 통과, 없으면 HTML_ONLY / NOT_INSTAGRAM_EXPORT.
- `ImportPipeline.validateAndScan(root)` → `Map<DomainType, List<Path>>` (FileScanner 가 glob 재귀 스캔).

**갭**: `data/` 를 모른다. 후보 탐지·목록 제공·선택 임포트 흐름이 없다. 사용자가 `data/instagram-...` 전체 경로를 손으로 쳐야 한다.

---

## 2. 설계 — 4개 빌딩 블록

### 2.1 설정 외부화 — `instagram.data.root`

`config/InstagramProperties` 에 `Data` 중첩 추가:

```yaml
instagram:
  data:
    root: ./data          # 배포: jar 기준 ./data. dev override: ../data
    auto-import-single: true   # 후보 1개면 자동 임포트할지(선택). 기본 true
    max-scan-depth: 2     # data/ 바로 아래~2단계까지만 후보 탐색(media 등 깊이 폭주 방지)
```

```java
@Getter @Setter
public static class Data {
    /** export 데이터 루트. 배포=./data, dev override=../data. */
    private String root = "./data";
    /** 후보가 정확히 1개면 사용자 선택 없이 자동 임포트. */
    private boolean autoImportSingle = true;
    /** data/ 하위 export 후보 탐색 최대 깊이. */
    private int maxScanDepth = 2;
}
```

> 도메인 불변값이 아닌 "정책"이므로 InstagramProperties 에 두는 것이 기존 컨벤션(explorer/message/web)과 일치.

### 2.2 탐지 서비스 — `ExportDiscoveryService` (신규, application 계층)

`data.root` 하위에서 **export marker 를 가진 디렉토리**를 후보로 수집.

- **marker 판별 재사용**: `ImportValidator` 의 known 도메인 판별 로직을 가볍게 재사용한다. 단 전체 스캔은 비싸므로(특히 `media/`), 후보 탐지는 **얕은 marker 체크**로 한다:
  - `data.root` 직속 하위 디렉토리 각각에 대해, 제한 깊이(`maxScanDepth`) 내에서 `following.json` / `followers_*.json` / `message_*.json` / `posts_*.json` / `login_activity*.json` 중 하나라도 있으면 export 후보로 간주.
  - 이는 `ImportValidator.validateExport` 의 "known 도메인 존재" 판정과 같은 의미를 더 싼 비용으로 수행하는 것 — 본 임포트 때 `validateAndScan` 으로 정식 재검증되므로 false positive 는 거기서 걸러진다.
- **zip 처리**: 같은 이름의 디렉토리가 이미 있으면 zip 은 무시. zip 만 있고 디렉토리가 없는 경우의 자동 압축해제는 **v1 범위 밖**(별도 결정 필요 — 아래 §5 미결).
- **반환**: 후보 DTO 목록 `List<ExportCandidate>`.

```java
public record ExportCandidate(
    String path,         // 절대 경로 (POST /api/import 에 그대로 넘길 값)
    String name,         // 디렉토리명 (instagram-{account}-{date}-{hash})
    String account,      // 파싱 가능하면 추출 (best-effort)
    String exportedAt,   // 디렉토리명에서 날짜 추출 (best-effort)
    long approxJsonCount // 얕은 카운트(선택, 정렬·표시용)
) {}
```

### 2.3 신규 엔드포인트 — `GET /api/import/candidates`

`ApiResponse<CandidatesResponse>` 봉투 사용(조회 계열). ※ 임포트 3종은 envelope 없이 직접 반환이지만, candidates 는 "조회"이므로 일반 envelope 채택 — 또는 일관성 위해 직접 반환. **권장: 직접 반환**(import 그룹과 일관).

```jsonc
// GET /api/import/candidates
{
  "dataRoot": "/abs/path/deploy/data",
  "candidates": [
    { "path": "/abs/.../instagram-myaccount-2026-06-03-igilBpS1",
      "name": "instagram-myaccount-2026-06-03-igilBpS1",
      "account": "myaccount", "exportedAt": "2026-06-03", "approxJsonCount": 42 }
  ]
}
```

프론트 분기:
- `candidates.length == 0` → "data/ 에 export 가 없습니다" 안내 + 수동 경로 입력 유도.
- `candidates.length == 1` && `autoImportSingle` → 바로 `POST /api/import` 호출(선택 UI 생략).
- `candidates.length >= 2` → **목록 보여주고 사용자가 1개 선택** → 선택 path 로 `POST /api/import`.

### 2.4 임포트 연결 (기존 재사용, 변경 최소)

선택된 후보의 `path` 를 **기존 `POST /api/import { folderPath }`** 에 그대로 전달.
→ `RealImportService.importFrom` → `ImportPipeline.validateAndScan` 정식 검증·스캔 → 백그라운드 파싱. **임포트 코어는 손대지 않는다.**
수동 override 경로도 동일 엔드포인트 → 자동/수동이 한 경로로 합류.

---

## 3. 흐름도

```
앱 시작 / Uploader 진입
        │
        ▼
GET /api/import/candidates   (data.root 얕은 스캔)
        │
   ┌────┴───────────────┬──────────────────────┐
   0개                  1개                     N개
   │                    │ autoImportSingle      │
   ▼                    ▼                        ▼
수동 경로 입력      자동 POST /api/import     목록 → 사용자 선택
   │                    │                        │
   └────────────────────┴──── folderPath ────────┘
                         ▼
              POST /api/import { folderPath }   (기존 그대로)
                         ▼
       validateAndScan(G1/G422) → 백그라운드 파싱 → status 폴링
```

---

## 4. 변경 파일 목록 (예상)

| 파일 | 변경 |
|------|------|
| `config/InstagramProperties.java` | `Data` 중첩 클래스 추가 |
| `src/main/resources/application.yaml` | `instagram.data.*` 블록 추가 |
| `application/imports/ExportDiscoveryService.java` | **신규** — 후보 탐지 |
| `application/imports/ExportCandidate.java` | **신규** — 후보 record |
| `api/imports/dto/CandidatesResponse.java` | **신규** — 응답 DTO |
| `api/imports/ImportController.java` | `GET /candidates` 핸들러 추가 |
| `api/imports/ImportAssembler.java` | 후보 → DTO 변환 추가(또는 별도 assembler) |
| (테스트) | DiscoveryService 단위 + candidates 엔드포인트 MockMvc |

> `ImportValidator` 의 marker 판별을 `ExportDiscoveryService` 가 공유하도록 **얕은 marker 체크 메서드**를 ImportValidator 또는 별도 util 로 추출하는 것을 권장(중복 방지).

---

## 5. 미결 / v2 후보

1. **zip 자동 압축해제** — `data/` 에 zip 만 있고 디렉토리가 없을 때 앱이 풀지 여부. v1 은 "압축 해제된 디렉토리만 후보"로 한정. (사용자: "여러 개면 사용자 선택"은 결정됐으나 zip-only 자동해제는 미결)
2. **dev vs 배포 root 차이** — dev `../data`, 배포 `./data`. 프로파일(`application-dev.yaml`)로 분리할지, 시작 시 둘 다 탐색할지.
3. **candidates envelope 일관성** — import 그룹(직접 반환) vs 조회 그룹(ApiResponse) 중 택1. 권장: import 그룹과 일관되게 직접 반환.
4. **재임포트 UX** — 후보 선택 후 이미 COMPLETED 상태면 덮어쓰기 확인. (도메인상 재임포트=덮어쓰기 허용)
5. **정렬 기준** — 후보 다중 시 디렉토리명 날짜 내림차순(최신 우선) 정렬 권장.

---

## 6. 근거 소스

- `infrastructure/scan/ImportValidator.java` (marker/known 도메인 판별 — KNOWN = FOLLOW/MESSAGE/ACTIVITY/LOGIN)
- `application/imports/ImportPipeline.java#validateAndScan`, `RealImportService.java#importFrom`
- `config/InstagramProperties.java`, `src/main/resources/application.yaml`
- `api/imports/ImportController.java`, `ImportAssembler.java`
- 배포 컨벤션: 사용자 결정(2026-06-04), 샘플 `data/instagram-myaccount-2026-06-03-igilBpS1/`
