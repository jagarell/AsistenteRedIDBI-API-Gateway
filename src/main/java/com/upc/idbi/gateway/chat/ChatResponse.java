package com.upc.idbi.gateway.chat;

import java.util.List;
import java.util.Map;

public record ChatResponse(
        String evaluationId,
        Integer currentStep,
        String currentQuestionKey,
        String currentQuestion,
        String currentInputType,
        List<String> currentOptions,
        Integer answeredQuestions,
        Integer totalQuestions,
        Integer progressPercent,
        Boolean completed,
        Map<String, String> answers,
        ChatProposal proposal
) {
}