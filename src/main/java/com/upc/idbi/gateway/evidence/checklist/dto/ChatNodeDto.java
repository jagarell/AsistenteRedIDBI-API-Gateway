package com.upc.idbi.gateway.evidence.checklist.dto;

/** Un nodo del chat (clave + texto de pregunta) — ver FastAPI GET /chat/nodes. */
public record ChatNodeDto(String key, String question) {
}
