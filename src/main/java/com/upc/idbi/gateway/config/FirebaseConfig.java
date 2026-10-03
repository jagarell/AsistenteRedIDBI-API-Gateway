package com.upc.idbi.gateway.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

/**
 * Inicializa el SDK Admin de Firebase (Cloud Messaging) al arrancar la app.
 * Si app.firebase.credentials-path no está configurado, el SDK queda sin
 * inicializar y PushNotificationService falla con un mensaje claro en vez de
 * simular el envío.
 */
@Component
public class FirebaseConfig {

    @Value("${app.firebase.credentials-path:}")
    private String credentialsPath;

    /** Contenido del JSON de la cuenta de servicio (útil en Railway, donde no hay un archivo que montar). */
    @Value("${app.firebase.credentials-json:}")
    private String credentialsJson;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        boolean hasJson = credentialsJson != null && !credentialsJson.isBlank();
        boolean hasPath = credentialsPath != null && !credentialsPath.isBlank();
        if (!hasJson && !hasPath) {
            return;
        }
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }
        try (InputStream serviceAccount = hasJson
                ? new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8))
                : new FileInputStream(credentialsPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            FirebaseApp.initializeApp(options);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo inicializar Firebase Admin SDK", e
            );
        }
    }
}
