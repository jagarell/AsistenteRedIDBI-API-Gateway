package com.upc.idbi.gateway.validation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Valida que el nombre de un negocio sea real, en dos niveles:
 * <ol>
 *     <li>Formato local (siempre activo, sin dependencias externas) — ver
 *     {@link #hasValidFormat(String)}.</li>
 *     <li>RUC contra un proveedor externo (APIs Peru, apis.net.pe) — apagado
 *     por defecto (`app.ruc-validation.enabled=false`); si falta la API key,
 *     {@link #validateRuc(String)} falla explícito, nunca simula éxito.</li>
 * </ol>
 * El nivel 2 hoy no se invoca desde ningún flujo real: el chat técnico
 * captura el NOMBRE del negocio, no un número de RUC, y no hay todavía un
 * nodo que pida el RUC — queda listo para cuando se agregue esa pregunta y
 * el usuario tenga la API key real (ver CONTEXTO_PROYECTO.md).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RucValidationService {

    private final RestTemplate restTemplate;

    @Value("${app.ruc-validation.enabled:false}")
    private boolean enabled;

    @Value("${app.ruc-validation.api-key:}")
    private String apiKey;

    private static final Pattern ONLY_DIGITS_OR_PUNCTUATION = Pattern.compile("^[\\d\\s.,-]*$");
    private static final Pattern REPEATED_CHAR = Pattern.compile("^(.)\\1*$");

    /** Validación de formato local, sin llamadas externas: nombre no vacío,
     * con longitud razonable, y que no sea solo dígitos/puntuación ni un
     * único carácter repetido (ej. "aaaaaa", "111111"). */
    public FormatValidationResult hasValidFormat(String name) {
        String trimmed = name == null ? "" : name.trim();

        if (trimmed.length() < 3) {
            return FormatValidationResult.fail("El nombre del negocio es demasiado corto.");
        }
        if (trimmed.length() > 150) {
            return FormatValidationResult.fail("El nombre del negocio es demasiado largo.");
        }
        if (ONLY_DIGITS_OR_PUNCTUATION.matcher(trimmed).matches()) {
            return FormatValidationResult.fail("El nombre del negocio no puede ser solo números.");
        }
        if (REPEATED_CHAR.matcher(trimmed.replace(" ", "")).matches()) {
            return FormatValidationResult.fail("El nombre del negocio no parece válido.");
        }
        return FormatValidationResult.ok();
    }

    /** Consulta real de RUC contra APIs Peru (apis.net.pe/v2/sunat/ruc). No
     * invocado hoy por ningún controller (ver javadoc de la clase) — queda
     * preparado para cuando el chat capture un número de RUC real. */
    public RucLookupResult validateRuc(String ruc) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            return RucLookupResult.notConfigured();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            var response = restTemplate.exchange(
                    "https://api.apis.net.pe/v2/sunat/ruc?numero=" + ruc,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );
            Map<?, ?> body = response.getBody();
            if (body == null || body.get("razonSocial") == null) {
                return RucLookupResult.notFound();
            }
            return RucLookupResult.found(String.valueOf(body.get("razonSocial")));
        } catch (Exception ex) {
            log.warn("No se pudo validar el RUC {} contra APIs Peru", ruc, ex);
            return RucLookupResult.error("No se pudo validar el RUC en este momento.");
        }
    }

    public record FormatValidationResult(boolean valid, String reason) {
        public static FormatValidationResult ok() {
            return new FormatValidationResult(true, null);
        }

        public static FormatValidationResult fail(String reason) {
            return new FormatValidationResult(false, reason);
        }
    }

    public record RucLookupResult(Status status, String businessName, String message) {
        public enum Status { FOUND, NOT_FOUND, NOT_CONFIGURED, ERROR }

        public static RucLookupResult found(String businessName) {
            return new RucLookupResult(Status.FOUND, businessName, null);
        }

        public static RucLookupResult notFound() {
            return new RucLookupResult(Status.NOT_FOUND, null, "No se encontró un negocio con ese RUC.");
        }

        public static RucLookupResult notConfigured() {
            return new RucLookupResult(
                    Status.NOT_CONFIGURED, null,
                    "Validación de RUC no configurada (falta RUC_VALIDATION_API_KEY)."
            );
        }

        public static RucLookupResult error(String message) {
            return new RucLookupResult(Status.ERROR, null, message);
        }
    }
}
