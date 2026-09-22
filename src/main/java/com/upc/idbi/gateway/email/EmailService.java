package com.upc.idbi.gateway.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Envío de correo real (recuperación de contraseña, propuestas técnicas) vía
 * la API HTTPS de Resend (resend.com) — no SMTP, porque el hosting (Railway,
 * plan Hobby) bloquea el puerto SMTP saliente y Resend evita ese problema y
 * el costo de subir a un plan superior. Desactivado por defecto
 * ({@code app.mail.enabled=false}): en ese caso lanza
 * {@link EmailDeliveryException} de inmediato, sin llamar a Resend. Se
 * activa configurando RESEND_API_KEY y MAIL_ENABLED=true.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String fromAddress;
    private final boolean enabled;

    public EmailService(
            RestTemplate restTemplate,
            @Value("${app.mail.resend-api-key}") String apiKey,
            @Value("${app.mail.from}") String fromAddress,
            @Value("${app.mail.enabled}") boolean enabled
    ) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.fromAddress = fromAddress;
        this.enabled = enabled;
    }

    public void sendSimple(String to, String subject, String body) {
        requireEnabled();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", fromAddress);
        payload.put("to", List.of(to));
        payload.put("subject", subject);
        payload.put("text", body);
        post(payload);
    }

    public void sendWithAttachment(
            String to,
            String cc,
            String subject,
            String body,
            byte[] attachmentBytes,
            String attachmentFilename
    ) {
        requireEnabled();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", fromAddress);
        payload.put("to", List.of(to));
        if (cc != null && !cc.isBlank()) {
            payload.put("cc", List.of(cc));
        }
        payload.put("subject", subject);
        payload.put("text", body);
        payload.put("attachments", List.of(Map.of(
                "filename", attachmentFilename,
                "content", Base64.getEncoder().encodeToString(attachmentBytes)
        )));
        post(payload);
    }

    private void post(Map<String, Object> payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(RESEND_API_URL, new HttpEntity<>(payload, headers), String.class);
        } catch (RestClientException e) {
            log.warn("Fallo al enviar correo vía Resend (destinatario omitido del log): {}", e.getMessage());
            throw new EmailDeliveryException("No se pudo enviar el correo", e);
        }
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new EmailDeliveryException(
                    "El envío de correo no está habilitado en este entorno " +
                            "(configura RESEND_API_KEY y MAIL_ENABLED=true)"
            );
        }
    }
}
