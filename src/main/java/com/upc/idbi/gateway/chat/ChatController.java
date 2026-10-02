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

    /** Responder un nodo EVIDENCE: 1 a 3 fotos por multipart (en vez de base64
     * dentro del JSON, para no inflar el payload del lado del cliente). El
     * gateway las convierte a base64 para la IA y las guarda como evidencia
     * (ver ChatService.answerChatWithPhotos). */
    @PostMapping(value = "/answer-photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ChatResponse answerWithPhotos(
            @PathVariable Long evaluationId,
            @RequestPart("files") List<MultipartFile> files,
            @RequestPart("state") String state
    ) {
        return chatService.answerChatWithPhotos(evaluationId, files, state);
    }
}
