package com.upc.idbi.gateway.conocimiento;

import com.upc.idbi.gateway.common.FastApiClient;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Asistente IA: mensaje libre, «¿Qué aprendiste hoy?», agregar reglas y preguntas, y fichas de aprendizaje. */
@RestController
@RequiredArgsConstructor
public class AsistenteController {

    private final AprendizajeService aprendizaje;
    private final FastApiClient fastApi;
    private final ReglaEmpresaRepository reglas;
    private final FichaConocimientoRepository fichas;

    private final com.upc.idbi.gateway.asistente.AsistenteChatService chat;

    /** Mensaje libre: responde con bloques (tarjetas) según la intención. Ver AsistenteChatService. */
    @PostMapping("/api/asistente/mensaje")
    public Map<String, Object> mensaje(@RequestBody Map<String, Object> req, @AuthenticationPrincipal AuthenticatedUser user) {
        return chat.mensaje(req, user);
    }

    /** «Redactar minuta» del borrador que armó el asistente. */
    @PostMapping("/api/asistente/redactar")
    public Map<String, Object> redactar(@RequestBody Map<String, Object> req, @AuthenticationPrincipal AuthenticatedUser user) {
        return chat.redactar(req, user);
    }

    /** Reporte de minutas del periodo en CSV (se abre en Excel). Técnico: lo suyo; supervisor: su equipo; administrador: todo. */
    @GetMapping(value = "/api/asistente/reporte.csv", produces = "text/csv;charset=UTF-8")
    public org.springframework.http.ResponseEntity<byte[]> reporteCsv(@org.springframework.web.bind.annotation.RequestParam String desde,
                                                                      @org.springframework.web.bind.annotation.RequestParam String hasta,
                                                                      @AuthenticationPrincipal AuthenticatedUser user) {
        String csv = chat.reporteCsv(user, java.time.LocalDate.parse(desde), java.time.LocalDate.parse(hasta));
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"reporte-minutas-" + desde + "_" + hasta + ".csv\"")
                .body(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @GetMapping("/api/aprendizaje/hoy")
    public Map<String, Object> hoy(@AuthenticationPrincipal AuthenticatedUser user) {
        return aprendizaje.hoy(user);
    }

    @PostMapping("/api/aprendizaje/aplicar")
    public Map<String, Object> aplicar(@RequestBody AplicarRequest req, @AuthenticationPrincipal AuthenticatedUser user) {
        return aprendizaje.aplicar(user, req.casos(), req.solo());
    }

    @PostMapping("/api/conocimiento/fichas")
    public Map<String, Object> fichas(@RequestBody FichasRequest req, @AuthenticationPrincipal AuthenticatedUser user) {
        return aprendizaje.fichas(user, req.casos(), req.empresa());
    }

    public record AplicarRequest(List<Map<String, Object>> casos, String solo) {
    }

    public record FichasRequest(List<Map<String, Object>> casos, String empresa) {
    }
}
