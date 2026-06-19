package com.instagram.analyze.application.message;

import com.instagram.analyze.domain.message.MessageStats;

/**
 * 다이렉트 메시지(3) 통계 서비스 (interface_plan §4.3).
 *
 * <p>원문(content)은 다루지 않고 통계 수치만 제공한다.
 */
public interface MessageService {

    /**
     * DM 전체 통계: 대화방 수·Top10·보낸/받은·initiator·시간대 분포. {@code GET /api/messages/stats}
     *
     * <p>전제: {@code requireCompleted()} + {@code requireOwnerResolved()}.
     * <p><b>owner 미해결 시 {@code OwnerNotResolved} 를 던진다(하드 게이트).</b> owner-독립 통계만
     * 담은 <b>부분 응답을 반환하지 않는다</b>. domain_exception 4.1 의 "보류"는 프론트가 호출 전에
     * 거는 사전 게이트(username 입력 먼저 요구)이지 이 메서드의 부분 응답 경로가 아니다(§4.3 / F).
     */
    MessageStats stats();
}
