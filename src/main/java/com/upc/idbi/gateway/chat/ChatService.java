package com.upc.idbi.gateway.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String FAST_API_URL =
            "http://localhost:8000";

    private final RestTemplate restTemplate;

    public ChatResponse startChat(Long evaluationId) {
        ChatStartRequest request = new ChatStartRequest(
                String.valueOf(evaluationId)
        );

        return restTemplate.postForObject(
                FAST_API_URL + "/chat/start",
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
                FAST_API_URL + "/chat/answer",
                normalizedRequest,
                ChatResponse.class
        );
    }
}