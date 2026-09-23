package com.upc.idbi.gateway.common;

import com.upc.idbi.gateway.auth.InvalidRefreshTokenException;
import com.upc.idbi.gateway.email.EmailDeliveryException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.UncheckedIOException;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleIllegalArgument(
            IllegalArgumentException exception
    ) {
        return Map.of(
                "message",
                exception.getMessage()
        );
    }

    /** Refresh token inválido/expirado — mismo status (401) y misma forma de
     *  JSON que el authenticationEntryPoint de SecurityConfig, para que el
     *  Android Authenticator lo trate igual que cualquier otro 401. */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> handleInvalidRefreshToken(
            InvalidRefreshTokenException exception
    ) {
        return Map.of(
                "message",
                exception.getMessage()
        );
    }

    @ExceptionHandler(EmailDeliveryException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> handleEmailDelivery(
            EmailDeliveryException exception
    ) {
        return Map.of(
                "message",
                exception.getMessage()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        String message = exception
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Datos inválidos");

        return Map.of("message", message);
    }

    /** Ruta que no existe (típico de un endpoint mal escrito en el cliente) —
     *  antes caía en el catch-all de abajo y se veía como un 500 genérico
     *  ("error inesperado") en vez de lo que realmente es: un 404. Este bug
     *  específico destapó que el Historial llamaba a una ruta que nunca
     *  existió (api/v1/evaluations en vez de api/evaluations). */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNoResourceFound(NoResourceFoundException exception) {
        return Map.of("message", "El recurso solicitado no existe.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Map<String, String> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        return Map.of("message", "El archivo es demasiado grande. Intenta con una foto más liviana.");
    }

    @ExceptionHandler({RestClientException.class, UncheckedIOException.class})
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> handleUpstreamFailure(Exception exception) {
        log.warn("Fallo de comunicación con un servicio externo", exception);
        return Map.of("message", "No se pudo completar la operación, intenta de nuevo en unos segundos.");
    }

    /** Última red de contención: cualquier excepción no manejada arriba cae
     * acá en vez de mostrarle al técnico un error opaco sin contexto. */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleUnexpected(Exception exception) {
        log.error("Error inesperado no manejado", exception);
        return Map.of("message", "Ocurrió un error inesperado. Intenta de nuevo.");
    }
}