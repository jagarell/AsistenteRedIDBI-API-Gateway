package com.upc.idbi.gateway.chat;

import java.util.Map;

public record ChatResponse(
        String evaluationId,
        Integer currentStep,
        String currentQuestionKey,
        String currentQuestion,
        Integer answeredQuestions,
        Integer totalQuestions,
        Integer progressPercent,
        Boolean completed,
        Map<String, String> answers,
        ChatProposal proposal
) {
}