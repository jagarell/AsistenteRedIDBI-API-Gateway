package com.upc.idbi.gateway.email;

/**
 * El correo no pudo enviarse (SMTP no configurado, o falla al conectar/enviar).
 * Nunca debe incluir el contenido del correo (código OTP, adjuntos) en el mensaje.
 */
public class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
