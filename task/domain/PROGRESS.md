# Instagram Analyzer — 빌드 진행 로그 (단일 진입점)

> 이 파일이 전체 진행의 타임라인 인덱스다. 단계별 상세는 각 문서 참조.
> 작업 방식: **단계를 작게 쪼개 → 구현 → compile/test → 리뷰 → 다음 단계**. 매 단계 리뷰 통과 후 진행.

## 문서 맵
| 문서 | 내용 |
|---|---|
| `0604_1424_domain_impl_plan_v1.md` | 도메인 모델 설계(계획) |
| `1530_domain_log.md` | 도메인 모델 생성 작업 로그 |
| `0604_1531_interface_plan_v1~v4.md` | 서비스/인터페이스 설계 (v1→v4 리뷰 정정 A~H, F~H) — **v4가 최신** |
| `PROGRESS.md`(이 파일) | 전체 단계 타임라인 + 이월/체크리스트 |
| `../api/260605_1512_api_task_status.md` | 2026-06-05 세션 — API(candidates·upload 엔드포인트) |
| `../application/260605_1512_application_task_status.md` | 2026-06-05 세션 — application(탐지·업로드 서비스·A2 가드) |
| `../config/260605_1512_config_task_status.md` | 2026-06-05 세션 — config(data·멀티파트·enum 컨버터·B1 YAML) |
| `../infrastructure/260605_1512_infrastructure_task_status.md` | 2026-06-05 세션 — infrastructure(ZipExtractor·스캐너 YAML·파서 4건) |

## 단계 타임라인
| 단계 | 산출물 | 상태 | 테스트 |
|---|---|---|---|
| 도메인 모델 | 엔티티·VO·DTO·Enum 47 (+보조타입 4) | ✅ | compile |
| ② 인터페이스 | application 서비스 9 + 포트 2(Read/Write) + Guard + Sourced | ✅ | compile |
| ③ stub | InMemoryImportStore·DefaultImportGuard·StubImportService + 조회 구현 9 | ✅ | 13 |
| API-a | ErrorResponse·ErrorCode·GlobalExceptionHandler + ImportController·Assembler | ✅ | 16 |
| API-b | ApiResponse envelope·ApiResultCode + 조회 컨트롤러 9·Assembler 9 | ✅ | 21 |
| **④a** | infrastructure 협력자 3 구현(DefaultFileScanner·DefaultStringNormalizer·DefaultAccountIdentityResolver) + Explorer mojibake 배선 | ✅ | 30 |
| ④b | 도메인별 Jackson 스트리밍 파서 | ✅ | 40 |
|    | └ 공통 베이스(AbstractJsonParser: streamArray/streamFirstArray + 노드 헬퍼) + ParseWarnings | ✅ | |
|    | └ FollowParser / MessageParser(분할 머지·owner분리·히트맵 싱크) | ✅ | |
|    | └ ActivityParser(post=creation_timestamp / like·saved=string_list_data / comment=string_map_data.Comment) · LoginParser(login/logout 구분) · SearchParser(ts nullable) · MiscLogParser(범용 키-값) | ✅ | |
| ④c | ETL 오케스트레이터 + 비동기 + 히트맵 사전계산 | ✅ | 49 |
|    | └ c-1: ImportValidator(G1) + owner participants 교집합(정확히 1명) + HeatmapAccumulator + ImportPipeline(validateAndScan/parse 분리) | ✅ | |
|    | └ c-2: RealImportService(@Service) — 동기 validateAndScan→markInProgress→백그라운드 parse→replaceAll/markFailed, resolveOwner 재파싱(onTimestamp no-op)+재임포트 안내, Executor 주입 | ✅ | |

> **백엔드 임포트 경로 end-to-end 완성** — 실제 export 폴더로 import → 6도메인 파싱·정규화·히트맵 사전계산 → 조회→HTTP. StubImportService 는 test 픽스처로 이동(프로덕션은 Real).

| Config 정비 | application.yaml(server.port·jackson non_null·logging·instagram.*) + InstagramProperties(@ConfigurationProperties: explorer·message) + CommonBeansConfig(Clock) + WebConfig(CORS 속성-게이트) + 전역 예외 fallback(500 INTERNAL_ERROR+로깅) + 비동기 import 실패 로깅 | ✅ | 53 |

> non_null 브릿지 Boot4+Jackson3 동작 테스트로 확인(키 부재 == null 계약). SPA forwarding·schema-mapping YAML 은 후속(이월). 프론트 서빙 미정이라 CORS 기본 off.

> read path(임포트 stub→조회→HTTP)는 API-b 까지 end-to-end 완결. 프론트가 실제 엔드포인트에 붙을 수 있음.

## 2026-06-05 세션 — 배포·업로드·실데이터 보강 (백엔드 임포트 경로 이후)
> 계층별 상세는 위 문서 맵의 `task/{api,application,config,infrastructure}/260605_1512_*` 참조.

| 작업 | 산출물 | 상태 | 테스트 |
|---|---|---|---|
| data/ 자동 탐지 | `ExportDiscoveryService`·`ExportCandidate` + `GET /api/import/candidates` + `instagram.data.*` | ✅ | 58 |
| ZIP 업로드 | `UploadService`/`RealUploadService`·`ZipExtractor`(zip-slip·재업로드 정리) + `POST /upload`·`/upload/status` + 멀티파트 스트리밍 설정 | ✅ | 65 |
| 실데이터 E2E 파서 수정 | A following(title 폴백)·B search(파일명·title)·C activity enum 대소문자(`IgnoreCaseEnumConverterFactory`)·D login(ISO title+IP 정규식) | ✅ | 71 |
| A1·A2 | threads/ 오분류 제외(스캐너) + EXTRACTING 중 import 거부(`UploadInProgressException`/409) | ✅ | 73 |
| B1 스캐너 YAML 외부화 | `instagram-schema-mapping.yaml` + `ScanMappingProperties`(@ConfigurationProperties) — DefaultFileScanner 하드코딩 제거 | ✅ | 73 |
| B2·B3·A3·B1b·테스트 | 빈방 필터(MessageParser) · owner-mismatch 휴리스틱(ImportPipeline, participants 복구→미해결) · data/ retention(`DataRetentionService`·`DataDirs`, keep=5) · FollowParser root-key YAML(`FollowMappingProperties`·`SchemaMappingYaml`) · upload/candidates MVC 테스트 | ✅ | 79 |

> 도메인 추가(소): `UploadStatus`(enum), `dto/CandidatesResponse`·`dto/UploadStatusResponse`.
> 실데이터 1.9GB export 로 라이브 검증(following 390·DM 144,217·logins 105·search 9·owner 자동식별).
> 프론트 연동은 별도 세션 — `doc/260605_frontend_integration_handoff.md`.

## 2026-06-06 세션 — label_values 적재 누락 수정 (likes·saved·posts)
> 상세: `task/infrastructure/260606_0031_infrastructure_task_status.md`

| 작업 | 산출물 | 상태 | 테스트 |
|---|---|---|---|
| 적재 누락 3건 | 파서가 **label_values 리스트형(최상위 ts+href)** 미지원 → liked_posts 636·saved 7·posts.json 141 전량 스킵하던 것 수정. `AbstractJsonParser.labelValue()` + ActivityParser addLike/addSaved/addPost 폴백 + posts.json 분기 | ✅ | 81 |
| 스캐너 정정 | YAML exclude `/ads_and_topics/`(ads `posts_viewed` ACTIVITY 오분류·헛경고 1.6k 제거) + ACTIVITY name-equals `posts.json` | ✅ | |
| close_friends 적재 | 동일 뿌리(label_values 폼) — FollowParser 에 `labelValuesUsername()`(username 라벨/최종 value) + 최상위 ts 폴백. close_friends 77건 복구, STRING_LIST_EMPTY 77→0 | ✅ | 82 |

> 라이브 재검증(2026-06-06): like 13→**649**, saved 0→**7**, post 1756→**1897**(posts.json 141), close_friends 0→**77**. import 잔여 경고 78→**1**(n=0 빈 파일만), ads 기인 경고 0.
> blocked_profiles(5)도 동일 폼이라 파서는 처리 가능하나 스캐너/`BLOCKED` 미정의로 미노출(의도).

## 2026-06-06 세션 — 전 데이터 노출 (각종 로그 catch-all)
> 상세: `task/config/260606_0115_config_task_status.md` · `task/infrastructure/260606_0031_*`

| 작업 | 산출물 | 상태 | 테스트 |
|---|---|---|---|
| catch-all 분류 | `ScanMappingProperties.classify()` 최종 fallback `UNKNOWN`→`MISC_LOG`. 전용 메뉴 없는 모든 파일이 '각종 로그'에 노출(새 메뉴 0) | ✅ | 84 |
| 범용 파서 강화 | `MiscLogParser`: `readRoot()` 로 배열/배열-프로퍼티-객체/단일객체 모두 처리 + **label_values 평면화**(label→value/href). `AbstractJsonParser.readRoot()` 추가 | ✅ | |

> 라이브: 각종 로그 **59파일·7,697레코드**(stories_viewed 1468·posts_viewed 1244·videos_watched 1240·ads_viewed 570 …), 광고/조회가 URL·캡션(한글 복구)·시각까지 표로. 활동 무회귀(post1897·like649), 경고 1건.

| 각종 로그 UX | `LogsView.tsx` MiscLogsSection 마스터-디테일 재작성: 파일 선택기(정렬·검색·행수배지) + 선택 1파일만 행검색·**페이지네이션 50행** + 시각 포맷. 서버 왕복 0, 페이지당 50행만 렌더 | ✅ | tsc·eslint·build |

> /api/logs 1회 페치(3.4MB/30ms)→인메모리 필터링. 상세: `task/frontend/260606_0129_frontend_task_status.md`.
> 후속(선택): 페이로드 ~20–30MB 초과 시 백엔드 지연 로딩(/api/logs 인덱스 + /{file}?page=) + LogFile relativePath.

| 전체 UI E2E | Playwright(일회성)로 SPA(:8374 동봉 서빙) 10개 메뉴 전부 클릭·스크린샷, 실데이터 카운트 화면 일치 확인 | ✅ | 콘솔/네트워크 에러 0 |
| 미디어 미리보기(①) | 탐색기에서 이미지/영상 미리보기. `GET /api/explorer/media`(스트리밍·Content-Type·zip-slip 가드) + tree 미디어 포함 + RawExplorer `<img>/<video>`, heic 안내. 트리 큰폴더 레이아웃 버그(`md:grid-rows-1`) 수정 | ✅ | 86 |
| 최근 언팔 누락 수정 | `recently_unfollowed_profiles.json` 은 **배열·root-key 없이 단일 객체 1건**(label_values 폼) → FollowParser 가 통째로 누락(0). `readRoot` 기반으로 배열/root-key/**단일객체** 3형태 처리. 최근언팔 0→**1(test_user2)**, 경고 1→0 | ✅ | 87 |
| 각종 로그 카드 UI | raw 키-값 덤프 → **카드형**(시각·인스타 링크·캡션 2줄 클램프+펼치기, 나머지 필드 작게). `MiscRow`+`classifyRow` | ✅ | build |
| **각종 로그 인사이트 재설계** | 평면 파일 피커 → **5개 의미 카테고리 서브탭**(조회/광고추적/스토리/계정보안/기타, 친화 한글 라벨). 지배적 '조회 이력'(~70%)에 **작성자 Top10·월별 추세·통합 타임라인(소스필터+페이지)** 집계. 광고추적=요약카드+드릴다운. row별 시각은 BE가 `LogFileView.timestamps` 신규 노출(어셈블러가 버리던 `LogRecord.timestamp`). 상세: `task/frontend/260606_1506_*`·`task/api/260606_1506_*` | ✅ | 87(BE) · tsc/eslint/build(FE) |
| 로그 분석기 디자인·구조 정리 | 967줄 단일 `LogsView.tsx` → **11개 파일 분해**(로직 `classify.ts`/`rows.ts` + 공용 프리미티브 `components.tsx`(SectionPanel·MetricChip·RankBarList·MiniBarChart·Pagination) + 섹션 7). '찌그러짐' 해소(GlassPanel 표면 통일·순위막대 고정트랙 정렬·칩 균일화). **이모지→lucide**(서브탭 KeyRound/Search/FileStack, 카테고리 Eye/Megaphone/Heart/Lock/Boxes). 기능 무변경. 상세: `task/frontend/260606_1527_*` | ✅ | tsc/eslint/build(FE) |
| 통계 칩 간격·친한친구 색 | 로그 통계 칩 평면 → 반응형 그리드+넉넉한 gap·폰트 축소(MetricChip), 카테고리 탭 간격 확대. 팔로우 '친한 친구' 모드를 `#00D53C`(유저네임·아바타 링·배지)로 강조 | ✅ | tsc/eslint/build(FE) |
| 각종 로그 '기타' media 복구 | archived_posts(134, 기타 60%+)가 **빈 카드**였던 것 수정 — 캡션·미디어·시각이 `media[]` 중첩이라 파서가 유실. `MiscLogParser`가 첫 미디어의 uri·title·creation_timestamp(+string_list_data 시각) 끌어올림. FE는 미디어 경로 감지→`/api/explorer/media` **썸네일 카드**(이미지 인라인·동영상/heic 아이콘) + ts 우선 시각. 상세: `task/infrastructure/260607_0106_*`·`task/frontend/260607_0106_*` | ✅ | 89(BE)·tsc/eslint/build(FE) |

> ⚠️ 검증 주의: IntelliJ 로 띄운 앱 인스턴스가 포트 8374 를 점유하면 `pkill -f bootRun` 으로 안 죽고 **구코드를 계속 서빙** → 변경이 반영 안 된 것처럼 보임. `lsof -ti tcp:8374 | xargs kill` 로 확실히 종료 후 재기동.

> SPA 서빙(C1 SpaForwardingController)·단일 동봉(C3 frontendBuild→static)·desktop 프로파일(C2) 이미 구현돼 있음 확인.
> 화면 검증: 개요·활동(1897/649/359/7)·팔로우(맞팔126·친한친구77)·DM(151방/144,217)·검색9·로그인105·히트맵·탐색기·각종로그(59파일, 페이지네이션) 모두 정상. 스크린샷 11장.

## 2026-06-06 세션 — 미디어 미리보기 · 최근언팔 · 시작분기 · 데이터 삭제/갱신

| 작업 | 산출물 | 상태 | 테스트 |
|---|---|---|---|
| 미디어 미리보기 | `GET /api/explorer/media`(스트리밍·zip-slip 가드)+tree 미디어 포함+RawExplorer img/video, heic 안내, 트리 레이아웃 버그(`md:grid-rows-1`) | ✅ | 86 |
| 최근 언팔 누락 | `recently_unfollowed_profiles.json`은 배열·root-key 없는 단일객체(label_values) → FollowParser `readRoot` 3형태 처리. 0→1(test_user2) | ✅ | 87 |
| 각종 로그 카드 UI | raw 덤프→카드(시각·인스타링크·캡션 2줄 클램프+펼치기, 나머지 작게). `MiscRow`/`classifyRow` | ✅ | |
| 시작 버튼 분기 | MainScreen: 임포트됨→/dashboard, 아니면→/upload. UploadScreen: data/ 후보 표시(1개 자동·N개 선택) | ✅ | |
| 데이터 삭제/갱신 | `DELETE /api/import`(인메모리 reset, 디스크 무관)+store/service `reset()`. Uploader "데이터 삭제"→확인→/main 잠김. 갱신=후보 재임포트(기존). `useImportFlow` 공유캐시 phase 오판(Uploader 튕김) 수정 | ✅ | 88 |

> 전 흐름 브라우저 E2E: 시작하기→/upload(후보3)→임포트→/dashboard→데이터삭제→/main, 콘솔에러 0.
> ⚠️ 검증 중 IntelliJ 인스턴스 포트 점유로 구코드 서빙되는 함정 재확인 — `lsof -ti tcp:8374|xargs kill` 필수([[project_intellij_port_8374]]).

### 2026-06-05 이월(미결)
- ✅ **A3·B2·B3·B1b·MVC 테스트 완료**(79 tests) — 아래는 남은 항목
- **C1** SPA forwarding · **C2** dev/배포 data.root 프로파일 분리 · **C3** 단일 JAR(프론트 동봉) — 프론트 진척 후
- zip-bomb 한도(노트만) · 파서 필드매핑(activity/login 등) 전면 YAML화는 후속(B1b는 follow root-key 까지)

## 확정 정책: timestamp 불량 처리 (도메인별 상이)
- **Follow / Search**: username·keyword 가 1차 키 → timestamp 불량이어도 **항목 유지**(ts nullable). `Timestamped` 미구현. **Overview 활동기간에서 제외**(1970 오염 방지).
- **Post / Like / Comment / Saved / Login**: timestamp 가 데이터 핵심(타임라인/히트맵) → 불량 시 **스킵**(TIMESTAMP_INVALID).
- 이유: 팔로우 close_friends/restricted/pending 은 실제 export 에서 ts 가 0/없음이 흔함 → 드롭하면 탭이 비고 맞팔·짝사랑 집합연산 왜곡.

## ④c 선결 (해소됨)
- ✅ **DM owner = participants 교집합 보강** — `MessageParser.resolveOwnerByParticipants`(정확히 1명·단일방 모호 제외), 파이프라인 우선순위 ①personal_information ②교집합. (잔여: personal_information 가 username 폴백으로 owner!=null 인데 전 방 sent==0인 엣지 탐지 휴리스틱은 후속 선택)
- ✅ **재파싱 onTimestamp no-op** — RealImportService.resolveOwner 가 `t -> {}` 전달(히트맵 이중가산 없음).
- ⬜ **빈 방(total=0) 필터링** — 미결(무해, 후속 선택).
- ✅ **durationMillis** — completedAt-startedAt 로 채움.

## 이월 / 미해결 항목
- **G1 임포트 경로·포맷 검증** throw·매핑 (IMPORT_PATH_NOT_FOUND/NOT_INSTAGRAM_EXPORT/HTML_ONLY) — ErrorCode 항목은 존재, ④c 에서 throw 연결
- **진짜 비동기 importFrom** — stub 은 동기. ④c 에서 markInProgress→백그라운드→replaceAll/markFailed
- **DM owner-first 재집계** — ④b/④c: message 파싱 owner-독립만 채움, resolveOwner 시 scannedFiles 재파싱→applyOwner, 디스크 실패 시 재임포트 안내
- **히트맵 임포트 시점 사전계산** — ④c: 5종(게시물·좋아요·댓글·DM·로그인) ts, DM owner 무관
- **검색 vs 각종로그 claim 순서** — ✅ ④a 스캐너가 logged_information/ 검색 파일 먼저 SEARCH 로 분류(테스트 검증)
- **Explorer mojibake** — ✅ ④a 에서 DefaultStringNormalizer 배선(0xFF 가드로 정상 UTF-8 무손상)
- **StringNormalizer strict 디코딩** — ✅ lenient(U+FFFD 손상) → strict 디코더(CodingErrorAction.REPORT)로 전환. 유효하지 않은 UTF-8 은 원본 유지(계약 준수), 테스트 추가
- **Jackson 3 (tools.jackson.*)** — Boot 4 는 Jackson 3.1.2 사용. 네임스페이스 `tools.jackson.databind`, `fields()`→`properties()`, `asText/isTextual`→`asString/isString`, ObjectMapper 는 `JsonMapper.builder().build()`. **스트림 중간 원소 읽기는 `parser.readValueAsTree()`** (mapper.readTree(parser) 는 trailing-token 검증으로 실패). ④b 파서에서 동일 적용
- **FileScanner 패턴 외부화** — 현재 코드 상수. instagram-schema-mapping.yaml 외부화는 후속(domain.md 목표)
- **AccountIdentityResolver DM 교차분석** — ④a 는 personal_information.json 만. participants 교차분석은 ④b(message 파싱) 보강
- **정규화 2종** — EpochMillis.normalize()(초/ms) + StringNormalizer(mojibake) 파싱 적용
- **테스트 격리** — ReadControllersTest @DirtiesContext BEFORE_EACH(컨텍스트 풀리로드). 테스트 늘면 store reset 훅으로 전환
- **FollowController analyze() 2회 호출** — 파생 타입 시 집합연산 중복(비블로킹)
- **프론트 계약** — 성공 envelope{code,message,data} vs 에러 ErrorResponse{code,message,detail,httpStatus} 2모양 (HTTP status 로 분기)
