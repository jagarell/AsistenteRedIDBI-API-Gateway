package com.upc.idbi.gateway.evidence.checklist.dto;

/** Una respuesta del chat técnico con el texto real de la pregunta (cruzando
 * las respuestas persistidas con la metadata de FastAPI GET /chat/nodes) —
 * reemplaza al viejo ChatResponseDto heredado de un motor de árbol ya
 * abandonado, que no calzaba con el formato real Map&lt;String,String&gt;. */
public record ChatAnswerDto(
        String nodeKey,
        String question,
        String answer
) {
}
