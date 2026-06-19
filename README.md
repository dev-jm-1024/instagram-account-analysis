# Instagram Account Analysis

> 인스타그램에서 내려받은 **내 계정 데이터(JSON export)** 를 업로드하면,
> 팔로우 관계·DM·활동·로그인 기록 등을 한눈에 분석·시각화해 주는 **로컬 전용** 대시보드입니다.

**🔒 모든 분석은 내 컴퓨터(localhost)에서만 처리됩니다.** 외부 서버로 데이터를 전송하지 않으며,
인터넷 연결 없이도 동작합니다. 단일 사용자용 포터블 앱입니다.

> 🤖 **이 프로젝트는 바이브 코딩(Vibe Coding) — AI 페어 프로그래밍으로 제작되었습니다.**

---

## 목차

- [무엇을 하는 앱인가](#무엇을-하는-앱인가)
- [주요 기능](#주요-기능)
- [기술 스택](#기술-스택)
- [시작하기](#시작하기)
  - [1. 인스타그램 데이터 내려받기](#1-인스타그램-데이터-내려받기)
  - [2. 앱 실행하기](#2-앱-실행하기)
  - [3. 데이터 업로드 & 분석](#3-데이터-업로드--분석)
- [개발자용 실행 가이드](#개발자용-실행-가이드)
- [REST API](#rest-api)
- [프로젝트 구조](#프로젝트-구조)
- [개인정보 보호](#개인정보-보호)

---

## 무엇을 하는 앱인가

인스타그램은 **Download Your Information** 기능으로 내 계정의 모든 활동 데이터(팔로워, DM, 좋아요,
검색 기록, 로그인 이력 등)를 JSON 묶음으로 내보낼 수 있습니다. 하지만 이 데이터는 수백 개의 JSON
파일로 흩어져 있고, 한글이 깨져(mojibake) 있는 등 그대로 읽기 어렵습니다.

이 앱은 그 export 묶음(ZIP)을 업로드하면:

- 흩어진 JSON 파일을 **스트리밍 파싱**해 (수 GB도 메모리 부담 없이) 분석하고
- 깨진 한글을 **자동 복구**하고
- 제각각인 타임스탬프를 **정규화**한 뒤
- **5개 그룹 · 10개 메뉴**의 대시보드로 시각화합니다.

---

## 주요 기능

| 그룹 | 메뉴 | 설명 |
|------|------|------|
| **핵심** | 📊 요약 대시보드 | 전체 통계 한눈에 보기 (팔로워 수, 활동량, 기간 등) |
| **관계** | 👥 팔로우 보기 | 맞팔·언팔·일방 팔로우 등 관계 분석, 친한 친구·차단·최근 언팔 |
| | 💬 DM 통계 | 대화방별 메시지 통계 (가장 많이 대화한 상대 Top N 등) |
| **활동** | 📄 활동 기록 | 게시물·좋아요·댓글·저장한 게시물 타임라인 |
| | 📅 활동 히트맵 | 요일 × 시간대별 활동 분포 히트맵 |
| | 🔍 검색 기록 | 검색어·프로필 검색 이력 |
| **계정** | 🛡️ 로그인 기록 | 로그인/로그아웃 이력, 접속 IP·기기 정보 |
| | 🗂️ 활동·추적 기록 | 광고 조회·관심사 등 기타 추적 로그를 표로 탐색 |
| **도구** | 🧩 데이터 탐색기 | 원본 JSON 파일을 트리로 직접 열람 (escape hatch) |
| | ☁️ 데이터 업로드 | export ZIP 업로드 / 자동 임포트 |

추가 특징:

- **대용량 처리** — 수 GB ZIP도 디스크 스트리밍으로 추출·파싱 (힙 평탄)
- **한글 깨짐 복구** — latin1 mis-encoding(mojibake) 자동 정규화
- **스키마 변형 대응** — 인스타그램 export 포맷의 여러 변형(배열/단일객체/label_values 등)을 견고하게 파싱
- **안전한 업로드** — zip-slip 방지, 추출 실패 시 orphan 정리

---

## 기술 스택

**백엔드**
- Java 21, Spring Boot 4.0.6 (Spring WebMVC)
- 스트리밍 JSON 파서 (Jackson) 기반 ETL 파이프라인
- 고정 포트 `8374` 로 동작하는 단일 사용자 로컬 서버

**프론트엔드**
- React 19, TypeScript, Vite 8
- Tailwind CSS 4, TanStack Query (react-query)
- Recharts (차트), Motion (애니메이션), lucide-react (아이콘)

**패키징**
- Vite 빌드 산출물이 Spring `static/` 에 동봉되어 **단일 JAR** 로 함께 서빙
- `jpackage` 기반 네이티브 배포본(.app / .exe) 생성 지원 — 최종 사용자는 **Java 설치 불필요**

---

## 시작하기

### 1. 인스타그램 데이터 내려받기

1. 인스타그램 → **설정 및 개인정보** → **계정 센터**
2. **내 정보 및 권한** → **내 정보 다운로드**
3. 형식은 반드시 **JSON** 으로 선택 (이 앱은 JSON 을 분석합니다 — HTML 아님)
4. 다운로드 링크가 오면 ZIP 파일을 받아 둡니다.

### 2. 앱 실행하기

**일반 사용자 (배포본)**
- 배포 ZIP 을 받아 압축을 풀고
  - macOS: `Instagram Analyzer.app` 더블클릭
  - Windows: `Instagram Analyzer.vbs` 더블클릭
- 콘솔 없이 브라우저가 자동으로 열립니다. (Java 설치 불필요)

**직접 빌드해서 실행 (Java 21 필요)**
```bash
./gradlew bootRun
# → http://localhost:8374 접속
```
> `bootRun` 은 프론트엔드까지 빌드해 함께 서빙합니다. (Node.js 필요. 백엔드만 빠르게 띄우려면 `-PskipFrontend`)

### 3. 데이터 업로드 & 분석

1. 앱이 열리면 **데이터 업로드** 화면에서 1번에서 받은 export ZIP 을 올립니다.
2. 추출·임포트가 끝나면 대시보드 메뉴를 자유롭게 탐색합니다.

> 또는 저장소 옆 `data/` 폴더에 export 를 두면 앱이 자동으로 후보를 발견합니다.

---

## 개발자용 실행 가이드

**사전 준비:** Java 21, Node.js

**핫 리로드 개발 모드** (프론트/백엔드 분리 실행)
```bash
# 터미널 1 — 백엔드 (8374)
./gradlew bootRun

# 터미널 2 — 프론트엔드 (5173, /api 는 8374 로 프록시)
cd frontend
npm install
npm run dev
# → http://localhost:5173
```

**단일 JAR 빌드**
```bash
./gradlew build
java -jar build/libs/instagram-analyze-0.0.1-SNAPSHOT.jar
# → http://localhost:8374
```

**테스트**
```bash
./gradlew test
```

**네이티브 배포본 생성** (최종 사용자 Java 불필요)
```bash
# macOS
./gradlew assembleDeploy -PwithRuntime

# Windows (Mac 에서 크로스 빌드)
./gradlew assembleDeploy -PwithRuntime -Ptarget=win \
  -PruntimeJdk="$(pwd)/build/cross-jdk/jdk-21.0.11+10" -PdeployDir=deploy-win
```

---

## REST API

모든 응답은 공통 `ApiResponse` envelope 로 감싸집니다.

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/api/overview` | 요약 통계 |
| `GET` | `/api/follows` | 팔로우 관계 (쿼리로 유형 필터) |
| `GET` | `/api/messages/stats` | DM 대화 통계 |
| `GET` | `/api/activity` | 활동 기록 (게시물·좋아요·댓글·저장) |
| `GET` | `/api/heatmap` | 요일×시간 활동 히트맵 |
| `GET` | `/api/searches` | 검색 기록 |
| `GET` | `/api/logins` | 로그인/로그아웃 이력 |
| `GET` | `/api/logs` | 기타 추적 로그 |
| `GET` | `/api/explorer/tree` · `/file` · `/media` | 원본 파일 트리 / 내용 / 미디어 |
| `POST` | `/api/import/upload` | export ZIP 업로드 (멀티파트, 스트리밍) |
| `GET` | `/api/import/upload/status` | 업로드·추출 진행 상태 |
| `GET` | `/api/import/candidates` | `data/` 자동 발견 후보 목록 |
| `POST` | `/api/import` | 후보 임포트 실행 |
| `GET` | `/api/import/status` | 현재 임포트 상태 |
| `DELETE` | `/api/import` | 임포트 데이터 초기화 |
| `POST` | `/api/import/owner` | 계정 소유자 지정 |

---

## 프로젝트 구조

```
instagram-analyze/
├── src/main/java/com/instagram/analyze/
│   ├── domain/           # 도메인 모델 (follow, message, activity, login, ...)
│   ├── application/      # 임포트 파이프라인·서비스
│   ├── infrastructure/   # 스트리밍 파서·스캐너·정규화·ZIP 추출
│   ├── api/              # REST 컨트롤러 (메뉴별)
│   └── config/           # 설정·YAML 외부화
├── src/main/resources/
│   ├── application.yaml              # 앱 설정 (포트 8374, 업로드 한도 등)
│   └── instagram-schema-mapping.yaml # 파일→도메인 분류 매핑
├── frontend/             # React + Vite 대시보드
│   └── src/
│       ├── features/     # 메뉴별 화면
│       ├── components/   # 공용 UI
│       └── services/api/ # 백엔드 API 클라이언트
├── data/                 # (gitignore) 내 인스타 export 를 두는 곳
└── build.gradle.kts
```

---

## 개인정보 보호

- 이 앱은 **완전히 로컬(localhost)** 에서만 동작합니다. 업로드한 데이터는 **외부로 전송되지 않습니다.**
- 분석 대상인 인스타그램 export 데이터(`data/`)는 저장소에 포함되지 않으며 `.gitignore` 로 제외됩니다.
- 외부 API 키·인증·서버 연동이 없습니다.

> ⚠️ 직접 fork·배포 시 `data/` 폴더(개인 export)와 추출물이 커밋되지 않도록 주의하세요.

---

## 제작 방식

이 프로젝트는 **바이브 코딩(Vibe Coding)** 으로 만들어졌습니다 — 사람이 요구사항·설계 방향을
주도하고, AI 와 페어 프로그래밍하며 구현·리팩터링·테스트를 함께 진행한 결과물입니다.

## 라이선스

개인 프로젝트입니다. 사용·수정은 자유이나, 분석하는 데이터의 민감성에 유의하세요.
