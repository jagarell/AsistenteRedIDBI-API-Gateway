package com.upc.idbi.gateway.chat;

import java.util.Map;

/**
 * "Corregir" lo leído en una evidencia y/o responder una pregunta de
 * confirmación del asistente (ver /chat/amend en FastAPI).
 */
public record ChatAmendRequest(
        String evaluationId,
        String state,
        String evidenceCode,
        String evidenceScope,
        Map<String, Object> fields,
        String clarificationKey,
        String clarificationAnswer
) {
}
