package com.upc.idbi.gateway.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final RestTemplate restTemplate;

    @Value("${app.fastapi.base-url}")
    private String fastApiBaseUrl;

    public ChatResponse startChat(Long evaluationId) {
        ChatStartRequest request = new ChatStartRequest(
                String.valueOf(evaluationId)
        );

        return restTemplate.postForObject(
                fastApiBaseUrl + "/chat/start",
                request,
                ChatResponse.class
        );
    }

    public ChatResponse answerChat(
            Long evaluationId,
            ChatAnswerRequest request
    ) {
        ChatAnswerRequest normalizedRequest =
                new ChatAnswerRequest(
                        String.valueOf(evaluationId),
                        request.currentStep(),
                        request.answer(),
                        request.answers() == null
                                ? new HashMap<>()
                                : request.answers()
                );

        return restTemplate.postForObject(
                fastApiBaseUrl + "/chat/answer",
                normalizedRequest,
                ChatResponse.class
        );
    }
}