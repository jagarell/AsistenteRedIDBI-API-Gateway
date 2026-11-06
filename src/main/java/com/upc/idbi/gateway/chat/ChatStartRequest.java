package com.upc.idbi.gateway.chat;

/** Inicio del chat. `technicianName` y `today` son los prefills de P07 y P06. */
public record ChatStartRequest(
        String evaluationId,
        String technicianName,
        String today,
        /** Nombre del sistema de impresiones de la empresa (regla SISIMP); nulo = el de siempre. */
        String sysImp
) {
}
