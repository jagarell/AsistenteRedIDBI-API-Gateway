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

    @PostMapping("/api/asistente/mensaje")
    public Map<String, Object> mensaje(@RequestBody Map<String, Object> req, @AuthenticationPrincipal AuthenticatedUser user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rol", user.isAdmin() ? "adm" : user.isSupervisor() ? "sup" : "tec");
        body.put("texto", req.getOrDefault("texto", ""));
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("reglas", reglas.findAll().stream().filter(ReglaEmpresa::isActiva).map(r -> r.getId() + ": " + r.getNombre() + ". " + r.getTexto()).toList());
        ctx.put("conocimiento", fichas.findAll().stream().map(f -> f.getCodigo() + ": " + f.getAprendizaje()).toList());
        body.put("contexto", ctx);
        Map<String, Object> r = fastApi.post("/asistente/mensaje", body);
        if ("aprendizaje".equals(r.get("tipo"))) {
            return aprendizaje.hoy(user);
        }
        return r;
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
