package com.upc.idbi.gateway.notification;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Envío de notificaciones push vía Firebase Cloud Messaging. Requiere que
 * app.firebase.credentials-path esté configurado (ver FirebaseConfig); si no
 * lo está, FirebaseMessaging.getInstance() falla explícito en vez de simular
 * el envío.
 */
@Service
public class PushNotificationService {

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
}
