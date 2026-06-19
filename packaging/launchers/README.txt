Instagram Analyzer — 실행 방법
================================================

이 폴더에 runtime/ 폴더가 있으면 Java 런타임이 동봉된 버전이라
Java 를 따로 설치하지 않아도 바로 실행됩니다.
runtime/ 가 없으면 Java 21 이상이 설치된 환경에서만 동작합니다.

[실행]  ─ 더블클릭하면 잠시 후 브라우저가 자동으로 열립니다(콘솔 창 없음)
  - Windows : "Instagram Analyzer.vbs" 더블클릭
  - macOS   : "Instagram Analyzer.app" 더블클릭
  - Linux   : ./start.sh

  열리지 않으면 직접 http://localhost:8080 으로 접속하세요.

[종료]  ─ 터미널 필요 없음
  메뉴바(mac) / 작업표시줄 트레이(Windows) 의 Instagram Analyzer 아이콘
  → 우클릭 → "종료"

[데이터 넣기]
  방법 1) 웹 화면에서 인스타그램 export ZIP 파일을 업로드  (가장 쉬움)
  방법 2) 이 폴더의 data/ 안에 export 폴더를 풀어 넣고 새로고침

[문제가 생기면]
  - Windows : start.bat 을 더블클릭하면 콘솔에 오류가 보입니다.
  - macOS   : start.command 를 더블클릭하면 터미널에 오류가 보입니다.
  - 로그 파일: 사용자 홈의 InstagramAnalyzer/app.log

[주의]
  - 처음 실행 시 보안 경고가 뜰 수 있습니다(미서명).
    · macOS  : .app 우클릭 → "열기" 로 한 번 허용
    · Windows: "추가 정보" → "실행"
  - Java 가 없고 runtime/ 도 없으면 https://adoptium.net 에서 Java 21+ 설치
