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
        String currentUnit,
        Integer answeredQuestions,
        Integer totalQuestions,
        Integer progressPercent,
        Boolean completed,
        Map<String, String> answers,
        ChatProposal proposal,
        // Campos leídos por IA de la foto recién respondida (ej. Mbps/ping/ISP
        // de una captura de speedtest) — solo viene poblado justo después de
        // responder un nodo PHOTO.
        Map<String, Object> lastPhotoResult,
        // Aviso cuando lo leído en la foto no coincide con lo ya respondido
        // antes en el chat (ej. ISP de la captura vs. proveedor tecleado).
        String crossValidationWarning
) {
}