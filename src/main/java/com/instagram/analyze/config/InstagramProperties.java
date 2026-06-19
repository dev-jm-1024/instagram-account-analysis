package com.instagram.analyze.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * 앱 튜닝값 외부화 (application.yaml 의 {@code instagram.*}).
 * 도메인 불변값(SECONDS_THRESHOLD·charset 등)이 아닌 "정책" 값만 담는다.
 */
@Getter
@Setter
@ConfigurationProperties("instagram")
public class InstagramProperties {

    private Data data = new Data();
    private Explorer explorer = new Explorer();
    private Message message = new Message();
    private Web web = new Web();

    @Getter
    @Setter
    public static class Data {
        /**
         * export 데이터 루트 (배포 컨벤션: jar 옆 {@code data/}). 배포=./data, dev override=../data.
         * 앱이 이 하위에서 Instagram export 디렉토리를 자동 탐지한다.
         */
        private String root = "./data";
        /** 후보가 정확히 1개면 사용자 선택 없이 자동 임포트 가능함을 프론트에 알리는 정책 힌트. */
        private boolean autoImportSingle = true;
        /**
         * data/ 의 각 후보 디렉토리에서 export marker 를 찾을 최대 깊이.
         * 표준 export 의 marker(connections/followers_and_following/following.json 등)는 깊이 3,
         * inbox 의 message_*.json 은 깊이 4 → 기본 4. media/ 깊이 폭주는 marker 발견 즉시 단락되어 무관.
         */
        private int maxScanDepth = 4;
        /**
         * data/ 에 유지할 export 최대 개수(retention). 업로드+추출 성공 시 최신순으로 정렬해
         * 이 개수만 남기고 오래된 export 디렉토리(+동명 .zip)를 삭제한다. 0 이하면 무제한(정리 안 함).
         */
        private int keep = 5;
    }

    @Getter
    @Setter
    public static class Explorer {
        /** 디렉토리 트리 최대 탐색 깊이 (domain.md 10). */
        private int maxDepth = 10;
        /** 단일 파일 응답 최대 바이트(초과 시 truncate). 기본 10MB. */
        private long maxFileBytes = 10L * 1024 * 1024;
    }

    @Getter
    @Setter
    public static class Message {
        /** DM 통계 Top N 대화방 (domain.md 3). */
        private int topN = 10;
    }

    @Getter
    @Setter
    public static class Web {
        private Cors cors = new Cors();

        @Getter
        @Setter
        public static class Cors {
            /** 비어있으면 CORS 비활성(운영=번들). dev 시 프론트 dev 서버 origin 추가. */
            private List<String> allowedOrigins = new ArrayList<>();
        }
    }
}
