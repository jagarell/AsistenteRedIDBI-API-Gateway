package com.upc.idbi.gateway.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/evaluations/{evaluationId}/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/start")
    public ChatResponse start(
            @PathVariable Long evaluationId
    ) {
        return chatService.startChat(evaluationId);
    }

    @PostMapping("/answer")
    public ChatResponse answer(
            @PathVariable Long evaluationId,
            @RequestBody ChatAnswerRequest request
    ) {
        return chatService.answerChat(
                evaluationId,
                request
        );
    }
}