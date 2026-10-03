package com.upc.idbi.gateway.notification;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Envío de notificaciones push vía Firebase Cloud Messaging. Requiere que
 * app.firebase.credentials-path o app.firebase.credentials-json estén
 * configurados (ver FirebaseConfig). Sin ellos no se envía nada: no se simula.
 */
@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    /** true si el SDK de Firebase está inicializado (hay credenciales). */
    public boolean isEnabled() {
        return !FirebaseApp.getApps().isEmpty();
    }

    /** Envía y propaga el error si FCM lo rechaza. */
    public void sendToToken(
            String deviceToken,
            String title,
            String body,
            Map<String, String> data
    ) {
        Message message = Message.builder()
                .setToken(deviceToken)
                .setNotification(
                        Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build()
                )
                .putAllData(data == null ? Map.of() : data)
                .build();

        try {
            FirebaseMessaging.getInstance().send(message);
        } catch (FirebaseMessagingException e) {
            throw new IllegalStateException("No se pudo enviar la notificación push", e);
        }
    }

    /**
     * Envía sin romper el flujo de negocio: un push que falla (o que no está
     * configurado) no debe impedir completar una minuta.
     *
     * @return true si FCM aceptó el mensaje
     */
    public boolean trySend(String deviceToken, String title, String body, Map<String, String> data) {
        if (deviceToken == null || deviceToken.isBlank()) {
            return false;
        }
        if (!isEnabled()) {
            log.debug("Push omitido: Firebase no está configurado");
            return false;
        }
        try {
            sendToToken(deviceToken, title, body, data);
            return true;
        } catch (RuntimeException e) {
            log.warn("No se pudo enviar el push: {}", e.getMessage());
            return false;
        }
    }
}
