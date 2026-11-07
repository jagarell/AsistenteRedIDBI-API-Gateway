package com.upc.idbi.gateway.chat;

/**
 * Editar una respuesta anterior. 0.0.15: la app usa siempre `reopen` (en curso o terminada): vuelve a preguntar solo esa,
 * con la respuesta anterior marcada, y al responder la API quita solo las respuestas que dependían de ella.
 * `rewind` (borrar desde esa pregunta en adelante) queda por compatibilidad.
 */
public record ChatEditRequest(
        String evaluationId,
        String state,
        String nodeId,
        String mode
) {
}
