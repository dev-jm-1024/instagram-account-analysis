@echo off
chcp 65001 >nul
rem Windows 더블클릭 실행용 런처. 자기 폴더(deploy\)로 이동해야 .\data 가 deploy\data 를 가리킨다.
cd /d "%~dp0"

rem 동봉 런타임(runtime\bin\java.exe) 우선, 없으면 시스템 java
set "JAVA=%~dp0runtime\bin\java.exe"
if not exist "%JAVA%" (
  where java >nul 2>nul
  if errorlevel 1 (
    echo Java 런타임을 찾을 수 없습니다.
    echo 동봉 런타임이 없는 배포본이면 https://adoptium.net 에서 Java 21+ 를 설치하세요.
    pause
    exit /b 1
  )
  set "JAVA=java"
)

echo Instagram Analyzer 를 시작합니다... 잠시 후 브라우저가 자동으로 열립니다.
echo 열리지 않으면 http://localhost:8374 으로 접속하세요. (종료: 트레이 아이콘 - 종료)
"%JAVA%" -Dspring.profiles.active=desktop -Djava.awt.headless=false -Dinstagram.data.root=./data -Dfile.encoding=UTF-8 -jar instagram-analyze.jar
pause
