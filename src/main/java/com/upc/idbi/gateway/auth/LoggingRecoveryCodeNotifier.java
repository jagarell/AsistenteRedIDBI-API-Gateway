package com.upc.idbi.gateway.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementación por defecto (desarrollo). Registra únicamente que se generó un
 * código para un correo enmascarado, SIN exponer el código ni el correo
 * completos. En producción debe sustituirse por un envío real vía el servicio
 * de correo corporativo.
 */
@Component
public class LoggingRecoveryCodeNotifier implements RecoveryCodeNotifier {

    private static final Logger log =
            LoggerFactory.getLogger(LoggingRecoveryCodeNotifier.class);

    @Override
    public void send(String email, String recoveryCode, int expiresInMinutes) {
        log.info(
                "Código de recuperación generado para {} (válido {} min). "
                        + "Pendiente integrar el envío por correo corporativo.",
                mask(email), expiresInMinutes
        );
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
