package com.upc.idbi.gateway.chat;

/**
 * Editar una respuesta anterior: `rewind` borra desde esa pregunta en adelante (chat en curso);
 * `reopen` vuelve a preguntar solo esa (evaluación terminada).
 */
public record ChatEditRequest(
        String evaluationId,
        String state,
        String nodeId,
        String mode
) {
}
