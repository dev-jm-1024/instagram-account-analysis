#!/bin/sh
# macOS 더블클릭 실행용 런처. 자기 폴더(deploy/)로 이동해야 ./data 가 deploy/data 를 가리킨다.
cd "$(dirname "$0")" || exit 1

# 동봉 런타임(runtime/bin/java) 우선, 없으면 시스템 java
if [ -x "./runtime/bin/java" ]; then
  JAVA="./runtime/bin/java"
elif command -v java >/dev/null 2>&1; then
  JAVA="java"
else
  echo "Java 런타임을 찾을 수 없습니다."
  echo "동봉 런타임이 없는 배포본이면 https://adoptium.net 에서 Java 21+ 를 설치하세요."
  echo "(이 창은 닫아도 됩니다)"
  read -r _ 2>/dev/null || true
  exit 1
fi

echo "Instagram Analyzer 를 시작합니다... 잠시 후 브라우저가 자동으로 열립니다."
echo "열리지 않으면 http://localhost:8374 으로 접속하세요. (종료: 메뉴바 트레이 아이콘 → 종료)"
exec "$JAVA" \
  -Dspring.profiles.active=desktop \
  -Djava.awt.headless=false \
  -Dinstagram.data.root=./data \
  -Dfile.encoding=UTF-8 \
  -jar instagram-analyze.jar
