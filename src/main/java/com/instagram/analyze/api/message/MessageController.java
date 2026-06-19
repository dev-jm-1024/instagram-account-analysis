package com.instagram.analyze.api.message;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.instagram.analyze.api.common.ApiResponse;
import com.instagram.analyze.application.message.MessageService;
import com.instagram.analyze.domain.message.dto.MessageStatsResponse;

/** GET /api/messages/stats (부록 A). owner 미해결 시 서비스가 던져 409 로 매핑된다. */
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;
    private final MessageAssembler assembler;

    public MessageController(MessageService messageService, MessageAssembler assembler) {
        this.messageService = messageService;
        this.assembler = assembler;
    }

    @GetMapping("/stats")
    public ApiResponse<MessageStatsResponse> stats() {
        return ApiResponse.ok(assembler.toResponse(messageService.stats()));
    }
}
