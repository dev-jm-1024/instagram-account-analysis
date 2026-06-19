# Instagram Analyzer — 배포 · 패키징 가이드

> 작성: 2026-06-05 22:37 · 대상: 이 앱을 일반 사용자에게 배포하는 사람(개발자)
> 한 줄 요약: **이 Mac 한 대에서, 외부 서버 없이, Java 설치 없이 더블클릭으로 켜지고 트레이로 꺼지는
> Windows/macOS 배포본을 만든다.**

---

## 0. TL;DR — 바로 배포하려면

```bash
# Windows 배포본 (메인 사용자층)
./gradlew assembleDeploy -PwithRuntime -Ptarget=win \
  -PruntimeJdk="$(pwd)/build/cross-jdk/jdk-21.0.11+10" -PdeployDir=deploy-win

# macOS 배포본
./gradlew assembleDeploy -PwithRuntime
```

- 산출물: `deploy-win/`, `deploy/` 폴더 → 각각 **통째로 zip** 해서 사용자에게 전달.
- 사용자: 압축 풀고 **`Instagram Analyzer.vbs`(Win) / `Instagram Analyzer.app`(mac)** 더블클릭.
  → 콘솔 없이 브라우저 자동 오픈 → 끌 땐 트레이 아이콘 "종료". **Java 설치 불필요.**

자세한 이유와 변형은 아래 참조.

---

## 1. 무엇을 풀어야 했나 (요구사항)

이 앱은 **포터블 단일 사용자 · 로컬 전용**이다(인스타그램 export 를 localhost 에서 분석, 외부 전송 0).
배포 시 풀어야 했던 제약은 다음과 같았다.

1. **개인정보** — 사용자 데이터(인스타 export)가 절대 외부로 나가면 안 됨. 빌드/배포도 외부 서버에 의존하기 싫음.
2. **일반 사용자** — Java 설치, 터미널 명령, 경로 입력을 못 한다고 가정. 더블클릭으로 끝나야 함.
3. **OS 무관** — Windows 사용자가 다수, macOS 도 일부.
4. **그래픽 only** — 켜면 바로 웹이 열리고, 끌 때도 터미널이 아니라 그래픽(트레이)으로.

---

## 2. 핵심 제약 — 왜 "하나로 다 되는" 게 불가능한가

Java 앱 배포에는 피할 수 없는 사실 두 가지가 있고, 이게 모든 선택을 지배했다.

### 2.1 JAR 은 OS 무관이지만 JRE 가 필요하다
`.jar` 한 파일은 Windows/macOS/Linux 어디서나 동작한다. **단, Java 런타임(JRE)이 깔려 있어야 한다.**
일반 사용자는 그게 없다 → 그래서 **런타임을 함께 동봉**해야 한다.

### 2.2 런타임(JRE)은 OS별 네이티브 바이너리다
Mac 용 `java` 는 Mac 에서만, Windows 용 `java.exe` 는 Windows 에서만 돈다.
→ **하나의 폴더 + 하나의 런타임으로 전 OS 커버는 원천적으로 불가능.** OS마다 폴더가 하나씩 필요하다
(`deploy/`=mac, `deploy-win/`=Windows).

> 이 두 사실 때문에 "단일 산출물 전-OS 네이티브 실행"은 포기하고, **OS별 자체 완결 폴더**를 만드는 방향으로 갔다.

---

## 3. 선택지 비교와 최종 결정

### 3.1 런타임을 어떻게 동봉할까

| 방식 | 형태 | 한 Mac 에서 전 OS 빌드? | 사용자 경험 | 채택 |
|---|---|---|---|---|
| **jpackage 네이티브 설치본** | `.dmg`/`.exe` 설치파일 | ❌ OS마다 그 OS에서 | ◎ 네이티브 | 보류 |
| **jlink 런타임 + GUI 런처** | 폴더 + `runtime/` + 런처 | ✅ **가능**(크로스 타깃) | ○ | **채택** |
| 풀 JDK 통째 복사 | 폴더 + `jdk/`(300MB) | ✅ | ○ | 비채택(무거움) |

**왜 jlink 크로스 타깃인가** — 요구 1(외부 의존 0) 때문이다.
`jpackage` 는 설치본의 네이티브 런처를 만들 때 **그 OS 가 있어야** 한다(Mac 에서 Windows `.exe` 못 만듦).
그러면 Windows 배포본을 위해 윈도우 머신이나 클라우드 CI(GitHub Actions)가 필요해진다.
반면 **`jlink` 는 타깃 OS 의 `jmods` 만 있으면 호스트와 다른 OS 용 런타임을 생성**할 수 있다.
즉 Windows JDK 를 내려받아 그 `jmods` 를 가리키면 **이 Mac 에서 Windows 런타임 폴더가 나온다.**
→ 윈도우 머신·클라우드 0 으로 전 OS 커버. 이게 이 프로젝트의 "로컬·외부의존 0" 취지에 정확히 맞았다.

> GitHub Actions 워크플로를 한때 만들었다가 **삭제**했다. 빌드는 소스코드만 다루지 사용자 데이터와 무관하지만,
> "외부 서버에 아무것도 안 올린다"는 원칙을 지키기 위해 완전 로컬로 통일했다.

### 3.2 콘솔 창을 어떻게 없앨까 (그래픽 only)

브라우저 자동 열기와 트레이 종료는 **원래 `desktop/DesktopLauncher`(@Profile `desktop`)에 이미 구현**돼 있었다.
남은 거친 점은 `.bat`/`.command` 더블클릭 시 **검은 콘솔/터미널 창**이 같이 뜨는 것이었다. 이걸 OS별 GUI 런처로 제거했다.

| OS | GUI 런처 | 원리 |
|---|---|---|
| Windows | `Instagram Analyzer.vbs` | `javaw.exe`(콘솔 없는 java)를 `WScript.Shell.Run cmd, 0, False`(창 숨김)로 기동 |
| macOS | `Instagram Analyzer.app` | `.app` 번들(Info.plist + `Contents/MacOS/run`) → Finder 가 Terminal 없이 실행 |
| Linux | `start.sh` | (리눅스 사용자는 터미널 허용도가 높아 스크립트 유지) |

각 OS의 콘솔 스크립트(`start.bat`/`start.command`)는 **문제 진단용**으로 같이 동봉한다(콘솔에 오류가 보임).
무콘솔이라 실패해도 화면에 안 보이므로, `desktop` 프로파일에 **파일 로깅**(`~/InstagramAnalyzer/app.log`)도 추가했다.

---

## 4. 동작 구조 (어떻게 굴러가나)

### 4.1 단일 JAR 안에 프론트까지
React(Vite) 빌드 결과가 백엔드 JAR 안에 동봉된다. 사용자는 jar 하나만 실행하면 풀스택이 뜬다.

```
frontend/src ──(npm run build)──▶ frontend/dist/
   └─(processResources)─▶ build/resources/main/static/
       └─(bootJar)─▶ instagram-analyze.jar : BOOT-INF/classes/static/
런타임: Spring 이 클래스패스 static/ 을 http://localhost:8374/ 로 서빙
```

### 4.2 배포 폴더 레이아웃

```
deploy-win/  (Windows)                     deploy/  (macOS)
├── Instagram Analyzer.vbs   ★더블클릭       ├── Instagram Analyzer.app   ★더블클릭
├── start.bat   (문제 진단용)                ├── start.command (문제 진단용)
├── runtime/    (동봉 JRE, ~64MB)            ├── runtime/    (동봉 JRE)
│    └ bin/javaw.exe · java.exe              │    └ bin/java
├── instagram-analyze.jar  (프론트 포함)      ├── instagram-analyze.jar
├── data/       (export 데이터 위치)          ├── data/
└── README.txt                              └── README.txt
```

### 4.3 실행 시 흐름
1. 사용자가 GUI 런처 더블클릭.
2. 런처가 **동봉 `runtime/bin/java(w)` 우선**(없으면 시스템 java)으로 jar 실행. 프로파일 `desktop` 활성.
3. `DesktopLauncher`(ApplicationReadyEvent) → **기본 브라우저로 `localhost:8374` 자동 오픈** + **시스템 트레이 아이콘**(열기/종료) 설치.
4. 종료: 트레이 → "종료" → Spring graceful shutdown → 프로세스 종료(터미널 불필요).

### 4.4 데이터 경로 규약
- **폴더 배포본(deploy/)**: 런처가 `-Dinstagram.data.root=./data` 로 **폴더 옆 `data/`** 를 가리킴(런처가 자기 폴더로 `cd` 후 실행).
- **jpackage 설치본**(쓸 경우): 앱 번들이 읽기전용이라 `~/InstagramAnalyzer/data` 사용(`application-desktop.yaml` 기본값).
- 어느 쪽이든 사용자는 **웹 UI 에서 ZIP 업로드**가 가장 쉬운 경로.

---

## 5. 빌드 명령 레퍼런스

### 5.1 Gradle 태스크 / 프로퍼티

| 태스크 | 설명 |
|---|---|
| `bootJar` | 프론트 포함 팻 JAR (`build/libs/instagram-analyze-<ver>.jar`) |
| `bundleRuntime` | jlink 로 동봉 런타임 생성(`build/runtime/`) |
| `assembleDeploy` | `deploy[-os]/` 묶음 조립(jar + 런처 + data/ + 선택적 runtime) |
| `jpackage` | (대안) JRE 동봉 네이티브 설치본 `.dmg`/`.exe` — 현재 채택 경로 아님 |

| 프로퍼티 | 의미 | 기본 |
|---|---|---|
| `-PwithRuntime` | 동봉 런타임 포함(Java 불필요본) | 미포함 |
| `-Ptarget=win\|mac\|linux` | GUI 런처를 어느 OS용으로 넣을지 | 빌드 머신 OS |
| `-PruntimeJdk=<JDK홈>` | jlink 가 읽을 jmods 소스(크로스 타깃) | 호스트 toolchain JDK |
| `-PdeployDir=<폴더명>` | 출력 폴더 이름 | `deploy` |
| `-PskipFrontend` | npm 프론트 빌드 생략(**검증용만!** 배포본엔 쓰지 말 것) | 미사용 |

### 5.2 실제 배포본 만들기

```bash
# macOS (arm64) — 이 Mac 기준 호스트 빌드
./gradlew assembleDeploy -PwithRuntime

# Windows x64 — 이 Mac 에서 크로스 빌드 (캐시된 JDK 사용)
./gradlew assembleDeploy -PwithRuntime -Ptarget=win \
  -PruntimeJdk="$(pwd)/build/cross-jdk/jdk-21.0.11+10" -PdeployDir=deploy-win

# Linux x64 — 필요 시 (Linux JDK 21 받아서)
./gradlew assembleDeploy -PwithRuntime -Ptarget=linux \
  -PruntimeJdk="/경로/jdk-21-linux" -PdeployDir=deploy-linux
```

> **반드시 `-PskipFrontend` 없이** 빌드해야 React 가 jar 에 들어간다. 확인: `unzip -l deploy*/instagram-analyze.jar | grep static/index.html`.

### 5.3 크로스 타깃용 JDK 준비
[adoptium.net](https://adoptium.net) 에서 **타깃 OS 의 JDK 21 `.zip`**(JRE 아님, `jmods` 포함된 JDK)을 받아 압축 해제 →
그 폴더 경로를 `-PruntimeJdk` 로 지정. 호스트와 **같은 major(21)** 여야 한다.
- 현재 캐시: `build/cross-jdk/jdk-21.0.11+10`(Windows x64). 재사용 가능.

---

## 6. 앞으로 배포할 때 (체크리스트)

새 버전을 낼 때마다 이 순서:

1. **코드/프론트 변경 반영** — 그냥 아래 빌드 명령을 돌리면 Gradle 이 프론트(npm)·jar 를 알아서 재빌드.
2. **빌드**
   ```bash
   ./gradlew assembleDeploy -PwithRuntime                                   # mac
   ./gradlew assembleDeploy -PwithRuntime -Ptarget=win \
     -PruntimeJdk="$(pwd)/build/cross-jdk/jdk-21.0.11+10" -PdeployDir=deploy-win   # win
   ```
3. **검증**
   - 프론트 동봉: `unzip -l deploy-win/instagram-analyze.jar | grep static/index.html`
   - Windows 런타임: `file deploy-win/runtime/bin/java.exe` → `PE32+ ... for MS Windows`
   - (mac) `open "deploy/Instagram Analyzer.app"` → 브라우저 열리고 트레이로 종료되는지
4. **패키징** — `deploy-win/`, `deploy/` 를 각각 zip.
5. **전달 + 안내 한 줄**
   - "압축 풀고 `Instagram Analyzer.vbs`(win) / `.app`(mac) 더블클릭. 보안 경고 뜨면 허용."
6. **(최초 실행 안내)** 미서명이라 첫 실행 경고:
   - Windows: "Windows의 PC 보호" → **추가 정보 → 실행**
   - macOS: `.app` **우클릭 → 열기**(한 번만)

---

## 7. 알아둘 점 / 한계

- **미서명** — 코드 서명을 안 했으므로 SmartScreen/Gatekeeper 경고는 정상. 사용자 규모가 커지면
  Windows 코드서명 인증서 / Apple Developer ID 서명을 추가 검토.
- **인텔(x64) Mac** — 현재 `deploy/` 런타임은 Apple Silicon(arm64) 전용. 인텔맥 사용자가 있으면
  x64 mac JDK 로 `-PruntimeJdk` 크로스 빌드 필요.
- **윈도우 실제 실행 검증** — 빌드 머신이 Mac 이라 `.exe`/`.vbs` 실행 자체는 검증 불가.
  생성·구조·로직까지만 확인됨 → **실제 Windows PC 에서 `.vbs` 더블클릭 동작 확인은 배포자 몫.**
- **무콘솔 실패 진단** — 화면에 안 보이면 `~/InstagramAnalyzer/app.log` 확인, 또는 `start.bat`/`start.command` 로 콘솔 띄워 오류 보기.
- **jpackage 경로** — 더 매끄러운 네이티브 설치본(.dmg/.exe)이 필요하면 `./gradlew jpackage` 가 그대로 있다.
  단 OS마다 그 OS에서 빌드해야 하고(크로스 불가), Windows `.exe` 는 WiX v3 필요.

---

## 8. 관련 파일 / 코드 지도

| 경로 | 역할 |
|---|---|
| `build.gradle.kts` | `bootJar`/`bundleRuntime`/`assembleDeploy`/`jpackage` 태스크 |
| `packaging/launchers/Instagram Analyzer.vbs` | Windows 무콘솔 GUI 런처 |
| `packaging/launchers/start.{bat,command,sh}` | 콘솔 런처(진단용 / Linux) |
| `packaging/launchers/README.txt` | 배포 폴더 안 사용자 안내문 |
| `packaging/macapp/Contents/{Info.plist,MacOS/run}` | macOS `.app` 번들 템플릿 |
| `src/main/resources/application-desktop.yaml` | `desktop` 프로파일(headless off · data root · 파일 로깅) |
| `src/main/java/.../desktop/DesktopLauncher.java` | 브라우저 자동 열기 + 시스템 트레이(열기/종료) |
| `build/cross-jdk/jdk-21.0.11+10` | 크로스 빌드용 Windows JDK(캐시, gitignore) |
| `.gitignore` | `deploy/`·`deploy-*/`·`build/` 무시 |

> 환경: Java 21 · Spring Boot 4.0.6 · Gradle 9.5.1 · Vite/React 프론트.
