package com.upc.idbi.gateway.auth;

import com.upc.idbi.gateway.email.EmailDeliveryException;
import com.upc.idbi.gateway.email.EmailService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Entrega el código de recuperación por correo real (vía {@link EmailService}).
 * Si el correo no está habilitado en este entorno (desarrollo) o falla al
 * enviarse, cae a un registro enmascarado — nunca se expone el código ni el
 * correo completos en logs, y el llamador (AuthService) siempre responde con
 * el mismo mensaje genérico sin importar si el envío tuvo éxito (evita
 * enumeración de usuarios).
 */
@Component
@RequiredArgsConstructor
public class EmailRecoveryCodeNotifier implements RecoveryCodeNotifier {

    private static final Logger log = LoggerFactory.getLogger(EmailRecoveryCodeNotifier.class);

    private final EmailService emailService;

    @Override
    public void send(String email, String recoveryCode, int expiresInMinutes) {
        String subject = "Código de recuperación de contraseña";
        String body = "Tu código de recuperación es: " + recoveryCode
                + "\nVence en " + expiresInMinutes + " minutos."
                + "\nSi no solicitaste este código, ignora este mensaje.";

        try {
            emailService.sendSimple(email, subject, body);
        } catch (EmailDeliveryException e) {
            log.info(
                    "No se pudo enviar el código de recuperación por correo para {} (válido {} min): {}",
                    mask(email), expiresInMinutes, e.getMessage()
            );
        }
    }

    /** Enmascara el correo: a***@d***.com (no se registra PII completa). */
    private String mask(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        String maskedLocal = local.charAt(0) + "***";
        String maskedDomain = domain.isEmpty() ? "***" : domain.charAt(0) + "***";
        return maskedLocal + "@" + maskedDomain;
    }
}
