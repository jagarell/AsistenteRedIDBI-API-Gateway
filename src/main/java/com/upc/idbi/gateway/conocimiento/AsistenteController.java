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

    /** Cancela un reporte programado (el botón «Cancelar el envío» de la tarjeta). */
    @PostMapping("/api/asistente/programados/{id}/cancelar")
    public Map<String, Object> cancelarProgramado(@org.springframework.web.bind.annotation.PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return chat.cancelarProgramado(id, user);
    }

    /** «Redactar minuta» del borrador que armó el asistente. */
    @PostMapping("/api/asistente/redactar")
    public Map<String, Object> redactar(@RequestBody Map<String, Object> req, @AuthenticationPrincipal AuthenticatedUser user) {
        return chat.redactar(req, user);
    }

    /** Reporte de minutas en Excel (.xlsx) según la consulta del Asistente IA. Respeta el rol: técnico lo suyo, supervisor su equipo, administrador todo. */
    @PostMapping(value = "/api/asistente/reporte.xlsx")
    public org.springframework.http.ResponseEntity<byte[]> reporteXlsx(@RequestBody Map<String, Object> consulta, @AuthenticationPrincipal AuthenticatedUser user) {
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"reporte-minutas.xlsx\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(chat.reporteXlsx(user, consulta));
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
