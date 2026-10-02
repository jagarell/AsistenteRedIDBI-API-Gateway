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
        // Estado opaco del flujo: la app lo devuelve en la siguiente respuesta.
        String state,
        // Nodo actual completo (tipo, opciones con value/label, bloque, etc.).
        Map<String, Object> node,
        // Si no es null, la respuesta fue inválida y el nodo no avanzó.
        String validationError,
        // Evidencia recién procesada: {code, scope, area, equipo, count, extracted}.
        Map<String, Object> lastEvidence,
        // Avisos de validación cruzada para mostrar en el chat.
        List<String> crossChecks,
        // Preguntas de confirmación tras una evidencia: [{key, text, options[]}].
        List<Map<String, Object>> followUps,
        // Solo E3: imágenes con las credenciales ya desenfocadas (base64). El
        // gateway guarda estas en vez de las originales y se las devuelve a la
        // app para que su miniatura tampoco muestre las credenciales.
        List<String> processedImages
) {
}
