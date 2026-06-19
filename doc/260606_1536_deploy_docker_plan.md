# Instagram Analyzer — Docker 배포판 계획 · 레지스트리 트레이드오프

> 작성: 2026-06-06 15:36 · 대상: 이 앱을 컨테이너로 배포하려는 사람(개발자)
> 한 줄 요약: **개발자가 `docker pull` 한 줄로 가져와 바로 띄우는** 로컬 전용 컨테이너 배포본을 만든다.
> 데이터(인스타 export·ZIP 업로드)는 **컨테이너 안 `/data` 볼륨(=호스트 폴더)에만** 저장되고 외부로 일절 나가지 않는다.

---

## 0. 결론 먼저 (TL;DR)

- **호스트 포트 = 8374** (네이티브 배포본과 동일). 컨테이너 내부도 8374로 통일. 접속: `http://localhost:8374`.
- **레지스트리 추천 = ① GHCR**. 단독으로 충분. 일반 사용자 도달 폭을 넓히고 싶을 때만 ③(둘 다)로 확장.
- **현재 `deploy-docker/`는 90% 완성**돼 있으나 **포트 버그(8080↔8374) 때문에 지금 그대로면 접속 불가** → 최우선 수정.
- "ZIP → 루트 `data/` 저장, 외부 전송 0" 요구사항은 **코드상 이미 충족**(아래 §2).

---

## 1. 현재 상태 점검 (2026-06-06 코드 대조)

`deploy-docker/` 에 이미 다음이 있다: `Dockerfile`(멀티스테이지), `docker-compose.yml`, `README.md`,
`start.command`/`start.bat`/`stop.*`(더블클릭 헬퍼), `data/`(호스트 볼륨). 루트 `.dockerignore` 도 있음.

### 1.1 잘 돼 있는 부분
- **멀티스테이지 빌드**: build 스테이지(JDK21 + Node20)에서 프론트 dist→static 동봉 + 팻 JAR, runtime 스테이지(JRE)만 남겨 경량화. 호스트에 Java/Node 불필요.
- **빌드 컨텍스트 = 루트**(`context: ..`), `.dockerignore` 가 `build/`·`node_modules`·`deploy*/`·`**/data` 제외 → 컨텍스트 비대화 방지.
- **데이터 볼륨**: `VOLUME ["/data"]` + ENTRYPOINT `--instagram.data.root=/data` + compose `./data:/data`.
- `-x test`(이미지 빌드 시 테스트 생략), `TZ=Asia/Seoul`, `-Dfile.encoding=UTF-8`.

### 1.2 🔴 반드시 고칠 버그 — 포트 불일치
앱은 컨테이너 안에서 **8374**로 뜬다(`application.yaml: server.port: 8374`, 고정).
그런데 Docker 설정은 전부 **8080**을 가리킨다:
- `Dockerfile: EXPOSE 8080`
- `docker-compose.yml: "8080:8080"` → 호스트 8080 → **컨테이너 8080(아무도 안 듣는 포트)**
- `README.md`, `start.command`, `start.bat` 의 `http://localhost:8080`

→ **브라우저 접속 시 연결 실패.** 컨테이너 내부를 네이티브와 동일한 **8374로 통일**하고 호스트도 8374로 매핑한다.

### 1.3 간극 — "pull 해서 바로"가 아직 없음
현재는 `build: context: ..` 구조라 개발자가 **소스를 받아 직접 빌드**해야 한다.
사용자가 원한 **`docker pull`만 하면 바로 실행**은 레지스트리 퍼블리시 + pull 기반 compose 가 있어야 한다(§3·§4).

---

## 2. 데이터 흐름 — "외부 전송 0" 검증 결과 (이미 충족)

`RealUploadService` 코드 확인:
```
업로드 ZIP → properties.getData().getRoot() 로 저장 → ZIP 비동기 전체 추출 → data/<basename>/
```
컨테이너에서 `instagram.data.root=/data`(ENTRYPOINT) 이므로:
```
[브라우저] --ZIP 업로드--> [컨테이너 :8374] --저장/추출--> /data (볼륨) == 호스트 deploy-docker/data/
```
- ZIP·추출된 export 는 **컨테이너 안 `/data`(=호스트 바인드 폴더)에만** 존재. 외부 네트워크 호출 없음(앱이 외부 API를 부르지 않음 → 코드 레벨 보장).
- 컨테이너를 지웠다 다시 만들어도 호스트 `data/` 는 보존.
- **추가 강화(권장)**: compose 포트 매핑을 `127.0.0.1:8374:8374`(루프백 바인드)로 → 같은 LAN의 타인도 접속 불가, 본인 PC에서만. "외부로 안 보낸다"를 네트워크 레벨에서도 한 번 더 보장.

> 참고: 웹에서 제안되던 `network_mode: none` 은 **포트 매핑까지 막아 접속이 불가**해지므로 쓰지 않는다. 루프백 바인드가 올바른 절충.

---

## 3. 레지스트리 선택 — 트레이드오프 분석

목표: 개발자가 `docker pull <주소>/instagram-analyze` → `docker run`(또는 compose) 으로 즉시 실행.
프로젝트 철학상 **빌드/푸시는 CI 없이 로컬 `docker buildx`** 로 한다(GitHub Actions 를 프라이버시 이유로 삭제한 전례와 일관).
이미지 자체는 소스코드만 담으므로 사용자 데이터와 무관하지만, "외부 서버에 자동화 파이프라인을 안 둔다"는 원칙을 유지.

### 3.1 ① GHCR (GitHub Container Registry) — `ghcr.io/<github-id>/instagram-analyze`

| 항목 | 평가 |
|---|---|
| 장점 | GitHub 계정 하나로 끝(별도 가입 X). 저장소와 **권한·소유권 연동**, 코드와 이미지가 한 곳. 공개 이미지는 **익명 pull 무제한·용량 사실상 무제한**. private 도 무료. 패키지 페이지가 README/버전과 자연 연결 |
| 단점 | pull 주소가 길다(`ghcr.io/...`). 푸시 인증에 **PAT(write:packages 스코프)** 필요. Docker Hub 대비 대중 인지도 낮음 |
| 적합 | 프로젝트가 **GitHub에 있고**, 주 대상이 **개발자**일 때 → 이 프로젝트에 가장 자연스러움 |

### 3.2 ② Docker Hub — `docker.io/<id>/instagram-analyze` (= `<id>/instagram-analyze`)

| 항목 | 평가 |
|---|---|
| 장점 | **가장 보편적**. pull 주소가 짧다(`docker run <id>/instagram-analyze`). 사람들이 "도커 이미지" 하면 먼저 찾는 곳. 검색 노출 |
| 단점 | **별도 계정** 필요. 무료 플랜 **private 1개 제한**(공개는 무제한). 익명 pull **rate limit**(IP당 시간당 횟수 제한) — 공유가 많아지면 사용자가 limit 만날 수 있음. 자동 빌드는 유료 경향 |
| 적합 | "개발자 아닌 사람도 docker만 깔려 있으면 쉽게" 까지 도달폭을 넓히고 싶을 때 |

### 3.3 ③ 둘 다 (GHCR + Docker Hub 동시 푸시)

| 항목 | 평가 |
|---|---|
| 장점 | 도달폭 최대(개발자=GHCR, 일반=Docker Hub). 한쪽 장애·정책변경 대비 이중화. publish 스크립트가 **같은 이미지에 태그 2개 붙여 양쪽 push** 하면 추가 빌드 비용 0 |
| 단점 | **계정·인증 2벌**(PAT + Docker Hub 토큰) 관리. README·문서에 주소 2개 → 사용자에게 약간의 혼란("어느 걸 쓰지?"). 버전 동기화 신경 |
| 적합 | 배포를 진지하게 키울 때. 초기엔 과할 수 있음 |

### 3.4 권장
- **시작은 ① GHCR 단독** — 이 프로젝트(GitHub 기반·개발자 대상)에 가장 잘 맞고, 계정/인증이 하나라 운영이 단순.
- publish 스크립트는 **처음부터 멀티 레지스트리 푸시가 가능하게** 설계(레지스트리 목록을 변수로). 나중에 Docker Hub 주소만 추가하면 **코드 변경 없이 ③로 확장**. → "지금은 GHCR, 필요하면 둘 다"가 비용 없이 열려 있음.
- 공개 범위: **public 이미지** 권장(익명 pull 가능해야 "pull 한 줄"이 성립). 소스가 이미 공개라면 이미지도 공개가 자연스러움.

---

## 4. 멀티 아키텍처 (필수)

대상 머신이 섞여 있다: **Apple Silicon(arm64)** + **인텔 Mac·일반 PC·서버(amd64)**.
단일 아키 이미지를 arm64에서 빌드해 올리면 amd64 사용자가 느린 에뮬레이션으로 돌거나 아예 실패한다.

→ `docker buildx` 로 **`linux/amd64,linux/arm64` 멀티플랫폼 매니페스트**를 빌드·푸시.
사용자는 아키 신경 안 쓰고 `docker pull` 하면 자기 아키에 맞는 레이어가 자동 선택됨.

```bash
# 최초 1회: 멀티아키 빌더 생성
docker buildx create --name iga-builder --use

# 빌드 + 푸시 (예: GHCR, public)
docker buildx build --platform linux/amd64,linux/arm64 \
  -f deploy-docker/Dockerfile \
  -t ghcr.io/<github-id>/instagram-analyze:1.0.0 \
  -t ghcr.io/<github-id>/instagram-analyze:latest \
  --push .
```
> 멀티아키는 **레지스트리에 직접 push** 해야 매니페스트가 생긴다(로컬 `docker images` 로는 단일 아키만 로드됨). 그래서 §3 레지스트리가 선행.

---

## 5. 사용자 경험 (pull 기반)

### 5.1 한 줄 실행 (docker run)
```bash
docker run -d --name instagram-analyze \
  -p 127.0.0.1:8374:8374 \
  -v "$(pwd)/data:/data" \
  ghcr.io/<github-id>/instagram-analyze:latest
# → http://localhost:8374
```

### 5.2 compose (pull 전용 — 빌드 없음)
`deploy-docker/` 에 **pull 용 compose 를 별도**로 둔다(기존 build 용과 분리, 또는 주석 토글):
```yaml
services:
  instagram-analyze:
    image: ghcr.io/<github-id>/instagram-analyze:latest
    container_name: instagram-analyze
    ports:
      - "127.0.0.1:8374:8374"     # 루프백 바인드(본인 PC 전용). LAN 공유하려면 "8374:8374"
    volumes:
      - ./data:/data
    environment:
      - TZ=Asia/Seoul
    restart: unless-stopped
```
`docker compose up -d` (← `--build` 없음) 한 줄. 이미지가 없으면 자동 pull.

> 기존 `docker-compose.yml`(build: context)는 **소스에서 직접 빌드하려는 기여자용**으로 유지하고,
> pull 용은 `docker-compose.pull.yml` 또는 README에 별도 명시. (둘의 용도가 다름)

---

## 6. 작업 계획 (구현 순서)

| # | 작업 | 파일 |
|---|---|---|
| 1 | **포트 버그 수정** — 내부 8374 통일 | `Dockerfile`(EXPOSE 8374), `docker-compose.yml`(127.0.0.1:8374:8374), `README.md`·`start.command`·`start.bat`(localhost:8374) |
| 2 | **pull 기반 실행 경로 추가** | `docker-compose.pull.yml`(또는 README 섹션) + `docker run` 예시 |
| 3 | **멀티아키 publish 스크립트** | `deploy-docker/publish.command`·`publish.bat` — buildx 멀티플랫폼 + 멀티 레지스트리(변수화) push, 버전 태그 인자 |
| 4 | **README 갱신** | pull/run/compose, 포트 8374, 데이터 보존, 루프백 바인드 설명, 레지스트리 주소 |
| 5 | (선택) **하드닝** | HEALTHCHECK(`/` 또는 `/api/import/status` 200 확인), 비-root USER, OCI 라벨(`org.opencontainers.image.*`), base 이미지 다이제스트 핀 |

### 6.1 결정 대기 항목
- **레지스트리 계정/주소** — GHCR `<github-id>` (또는 Docker Hub `<id>`). 위 §3.4 권장은 GHCR 단독 시작.
- **버전 태깅 규칙** — `1.0.0` + `latest` 권장(SemVer). 첫 릴리스 버전 번호.
- **public/private** — pull 한 줄이 성립하려면 public 권장.

---

## 7. 네이티브 배포본과의 관계 (혼동 방지)

| 배포 형태 | 대상 | 산출물 | 비고 |
|---|---|---|---|
| `deploy/`·`deploy-win/`·`deploy-linux/` | **일반 사용자**(Java 모름) | jlink 런타임 + GUI 런처(.vbs/.app) | 더블클릭·브라우저 자동 열기·트레이 종료. `260605_2237_deploy_guide.md` |
| **`deploy-docker/`** | **개발자·자가호스팅·NAS** | 컨테이너 이미지(레지스트리) | 브라우저 자동 열기·트레이 **없음** → 수동 접속·`compose down` 종료 |

- 컨테이너엔 데스크톱이 없어 `DesktopLauncher`(브라우저 열기/트레이)는 동작 안 함 → `desktop` 프로파일을 켜지 않는다(현재 ENTRYPOINT 도 desktop 미지정, 정상).
- 데이터 경로: 네이티브 폴더본=`./data`(jar 옆), docker=`/data`(볼륨). 둘 다 `instagram.data.root` 한 스위치로 통일됨.

---

## 8. 함정 / 체크리스트

- [ ] 컨테이너 내부 포트 8374 통일 — 매핑은 `호스트:8374`(컨테이너측은 8374 고정)
- [ ] 멀티아키는 **레지스트리 push 필수**(로컬 load 로는 매니페스트 안 생김)
- [ ] public 이미지여야 익명 `docker pull` 성립
- [ ] 멀티파트 무제한(`max-file-size: -1`) 이미 설정됨 → 대용량 ZIP OK. 단 **호스트 디스크 여유** 안내(미디어 포함 GB 단위 추출)
- [ ] `.dockerignore` 가 `**/data`(1.9GB 샘플) 제외 확인 — 빌드 컨텍스트 비대화 방지(현재 OK)
- [ ] 첫 빌드 시간 길다(Node ci + Vite build + bootJar) → README에 "처음 몇 분" 안내(현재 있음)
- [ ] 종료/데이터: `compose down` 해도 호스트 `data/` 보존됨을 명시
