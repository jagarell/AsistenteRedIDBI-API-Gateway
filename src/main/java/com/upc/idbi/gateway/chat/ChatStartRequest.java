package com.upc.idbi.gateway.chat;

/** Inicio del chat. `technicianName` y `today` son los prefills de P07 y P06. */
public record ChatStartRequest(
        String evaluationId,
        String technicianName,
        String today
) {
}
