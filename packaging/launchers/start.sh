#!/bin/sh
# Linux 실행용 런처. (파일 관리자에서 더블클릭 → "실행" 또는 터미널에서 ./start.sh)
cd "$(dirname "$0")" || exit 1

# 동봉 런타임(runtime/bin/java) 우선, 없으면 시스템 java
if [ -x "./runtime/bin/java" ]; then
  JAVA="./runtime/bin/java"
elif command -v java >/dev/null 2>&1; then
  JAVA="java"
else
  echo "Java 런타임을 찾을 수 없습니다. 동봉 런타임이 없으면 Java 21+ 를 설치하세요."
  echo "예) sudo apt install openjdk-21-jre   또는   https://adoptium.net"
  exit 1
fi

echo "Instagram Analyzer 를 시작합니다... 브라우저에서 http://localhost:8374 으로 접속하세요."
exec "$JAVA" \
  -Dspring.profiles.active=desktop \
  -Djava.awt.headless=false \
  -Dinstagram.data.root=./data \
  -Dfile.encoding=UTF-8 \
  -jar instagram-analyze.jar
