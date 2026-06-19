package com.instagram.analyze.application.support;

/**
 * 임포트 상태 게이트 (interface_plan §3.2). G3 분기(503 vs 200 importRequired)를 한 곳에 모은다.
 */
public interface ImportGuard {

    /**
     * 임포트 미완료면 던진다 → {@code IMPORT_NOT_COMPLETED}(503). [히트맵 등]
     */
    void requireCompleted();

    /**
     * 임포트 미완료면 true (에러 아님, HTTP 200 + importRequired). [개요]
     * 게이트 걸린 형제 서비스를 호출하기 <b>전에</b> 먼저 확인해야 한다(§4.9).
     */
    boolean isImportRequired();

    /**
     * 본인식별 미해결이면 던진다(하드 게이트, 부분 응답 아님). [DM]
     * "보류"는 프론트가 호출 전에 거는 사전 게이트이며 이 메서드는 부분 응답을 허용하지 않는다(§4.3).
     */
    void requireOwnerResolved();
}
