package com.upc.idbi.gateway.chat;

import java.util.List;

/**
 * Respuesta al nodo actual. `state` es el estado opaco que devolvió la
 * respuesta anterior (el motor no guarda estado en el servidor). Para nodos
 * EVIDENCE `photosBase64` lleva 1 a 3 fotos; `answer` se ignora en ese caso.
 */
public record ChatAnswerRequest(
        String evaluationId,
        String state,
        String answer,
        List<String> photosBase64
) {
}
