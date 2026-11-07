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
        List<String> photosBase64,
        // Cómo se consiguió la foto: "tomar" (cámara), "subir" (galería) o "forzar" («Guardar como correcta»).
        String mode,
        // Nota opcional de «Guardar como correcta».
        String nota,
        // Foto pendiente de «Subir después» (EV#Calientes) que se sube al final; null = nodo actual.
        String nodeId,
        // 0.0.15: descripción opcional de la foto («¿Qué muestra esta foto?»). Sale debajo de la foto en la minuta.
        String descripcion
) {
}
