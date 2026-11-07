package com.upc.idbi.gateway.chat;

import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/evaluations/{evaluationId}/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/start")
    public ChatResponse start(
            @PathVariable Long evaluationId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return chatService.startChat(evaluationId, user);
    }

    @PostMapping("/answer")
    public ChatResponse answer(
            @PathVariable Long evaluationId,
            @RequestBody ChatAnswerRequest request
    ) {
        return chatService.answerChat(evaluationId, request);
    }

    /** Corregir lo leído en una evidencia o responder una confirmación del asistente. */
    @PostMapping("/amend")
    public ChatResponse amend(
            @PathVariable Long evaluationId,
            @RequestBody ChatAmendRequest request
    ) {
        return chatService.amendChat(evaluationId, request);
    }

    /** Editar una respuesta anterior (rewind: chat en curso · reopen: evaluación terminada). */
    @PostMapping("/edit")
    public ChatResponse edit(
            @PathVariable Long evaluationId,
            @RequestBody ChatEditRequest request
    ) {
        return chatService.editChat(evaluationId, request);
    }

    /** Adjunto del cliente en el Chat IA (ficha RUC, comprobante, recibo, tarjeta, letrero): devuelve lo leído para revisar. */
    @PostMapping(value = "/adjunto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public java.util.Map<String, Object> adjunto(
            @PathVariable Long evaluationId,
            @RequestPart("files") List<MultipartFile> files,
            @RequestPart("state") String state,
            @RequestPart(value = "tipo", required = false) String tipo
    ) {
        return chatService.leerAdjunto(evaluationId, files, state, tipo);
    }

    /** Usa los datos revisados: responde la pregunta actual y deja las siguientes para cuando el chat llegue. */
    @PostMapping("/adjunto/usar")
    public ChatResponse usarAdjunto(
            @PathVariable Long evaluationId,
            @RequestBody java.util.Map<String, Object> body
    ) {
        return chatService.usarAdjunto(evaluationId, body);
    }

    /** Responder un nodo EVIDENCE: 1 a 3 fotos por multipart (en vez de base64
     * dentro del JSON, para no inflar el payload del lado del cliente). El
     * gateway las convierte a base64 para la IA y las guarda como evidencia
     * (ver ChatService.answerChatWithPhotos). */
    @PostMapping(value = "/answer-photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ChatResponse answerWithPhotos(
            @PathVariable Long evaluationId,
            @RequestPart("files") List<MultipartFile> files,
            @RequestPart("state") String state,
            @RequestPart(value = "mode", required = false) String mode,
            // «Guardar como correcta»: mode = "forzar" y la nota opcional del técnico
            @RequestPart(value = "nota", required = false) String nota,
            // «Subir después»: la foto pendiente que se sube al final (EV#Calientes)
            @RequestPart(value = "nodeId", required = false) String nodeId,
            // 0.0.15: descripción opcional de la foto
            @RequestPart(value = "descripcion", required = false) String descripcion
    ) {
        return chatService.answerChatWithPhotos(evaluationId, files, state, mode, nota, nodeId, descripcion);
    }

    /** 0.0.15: agregar, editar o borrar (descripcion vacía) la descripción de una evidencia. Body: {state, nodeId, descripcion}. */
    @PostMapping("/evidence-note")
    public ChatResponse evidenceNote(
            @PathVariable Long evaluationId,
            @RequestBody java.util.Map<String, Object> body
    ) {
        return chatService.evidenceNote(evaluationId, body);
    }
}
