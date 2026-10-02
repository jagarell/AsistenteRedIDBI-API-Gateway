package com.upc.idbi.gateway.chat;

import java.util.Map;

public record ChatAnswerRequest(
        String evaluationId,
        Integer currentStep,
        String answer,
        Map<String, String> answers,
        // Presente solo cuando el nodo actual es PHOTO (ver ChatService.answerChatWithPhoto).
        String photoBase64
) {
}