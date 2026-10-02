package com.upc.idbi.gateway.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/evaluations/{evaluationId}/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/start")
    public ChatResponse start(
            @PathVariable Long evaluationId
    ) {
        return chatService.startChat(evaluationId);
    }

    @PostMapping("/answer")
    public ChatResponse answer(
            @PathVariable Long evaluationId,
            @RequestBody ChatAnswerRequest request
    ) {
        return chatService.answerChat(
                evaluationId,
                request
        );
    }

    /** Responder un nodo PHOTO (ej. captura de speedtest) — la foto viaja
     * como multipart en vez de base64 en el JSON para no inflar el payload
     * del lado del cliente; el gateway hace la conversión a base64 antes de
     * reenviar a FastAPI (ver ChatService.answerChatWithPhoto). */
    @PostMapping(value = "/answer-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ChatResponse answerWithPhoto(
            @PathVariable Long evaluationId,
            @RequestPart("file") MultipartFile file,
            @RequestPart("currentStep") String currentStep,
            @RequestPart("answersJson") String answersJson
    ) {
        return chatService.answerChatWithPhoto(
                evaluationId,
                file,
                Integer.parseInt(currentStep),
                answersJson
        );
    }
}