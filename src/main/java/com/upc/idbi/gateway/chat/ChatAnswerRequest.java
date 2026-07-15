package com.upc.idbi.gateway.chat;

import java.util.Map;

public record ChatAnswerRequest(
        String evaluationId,
        Integer currentStep,
        String answer,
        Map<String, String> answers
) {
}