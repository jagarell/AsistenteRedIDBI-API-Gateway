package com.upc.idbi.gateway.empresa;

import com.upc.idbi.gateway.bitacora.BitacoraService;
import com.upc.idbi.gateway.minuta.Minuta;
import com.upc.idbi.gateway.minuta.MinutaRepository;
import com.upc.idbi.gateway.notification.UserNotification;
import com.upc.idbi.gateway.notification.UserNotificationRepository;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Empresa (doc 0.0.7 §16, §31): nombre y logo de la minuta, ambiente demo/producción y copia de seguridad.
 * Todos los roles la leen (para el nombre, el logo y la etiqueta DEMO); solo el administrador la cambia.
 */
@RestController
@RequestMapping("/api/empresa")
@RequiredArgsConstructor
public class EmpresaController {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EmpresaService service;
    private final EmpresaRepository repo;
    private final MinutaRepository minutas;
    private final UserNotificationRepository notificaciones;
    private final BitacoraService bitacora;

    @GetMapping
    public Map<String, Object> get() {
        Empresa e = service.get();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("nombre", service.nombre());
        r.put("logo", service.logoDataUri());
        r.put("enProduccion", e.getProduccionDesde() != null);
        r.put("produccionDesde", e.getProduccionDesde() == null ? null : e.getProduccionDesde().format(DMY));
        r.put("produccionPor", e.getProduccionPor());
        Map<String, Object> copia = new LinkedHashMap<>();
        copia.put("frecuencia", e.getCopiaFrecuencia() == null ? "off" : e.getCopiaFrecuencia());
        copia.put("hora", e.getCopiaHora() == null ? 2 : e.getCopiaHora());
        copia.put("correo", e.getCopiaCorreo() == null ? "" : e.getCopiaCorreo());
        copia.put("incluyeFotos", Boolean.TRUE.equals(e.getCopiaIncluyeFotos()));
        copia.put("ultima", e.getCopiaUltima() == null ? null : e.getCopiaUltima().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        r.put("copia", copia);
        List<Minuta> todas = minutas.findAll();
        Map<String, Object> demo = new LinkedHashMap<>();
        demo.put("minutas", todas.stream().filter(m -> "demo".equals(m.getEtapa()) || e.getProduccionDesde() == null).count());
        demo.put("conservadas", todas.stream().filter(m -> "demo".equals(m.getEtapa()) && service.visible(m)).count());
        r.put("baseConocimientoDemo", demo);
        return r;
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public Map<String, Object> nombre(@RequestBody Map<String, Object> req, @AuthenticationPrincipal AuthenticatedUser user) {
        String n = String.valueOf(req.getOrDefault("nombre", "")).trim();
        if (n.isBlank() || n.length() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre de la empresa es obligatorio (hasta 150 caracteres)");
        }
        Empresa e = service.get();
        e.setNombre(n);
        repo.save(e);
        bitacora.registrar(user, null, "empresa", "Cambió el nombre de la empresa a " + n);
        return get();
    }

    /** Logo PNG o JPG de hasta 2 MB, en base64. */
    @PutMapping("/logo")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public Map<String, Object> logo(@RequestBody Map<String, Object> req, @AuthenticationPrincipal AuthenticatedUser user) {
        String b64 = String.valueOf(req.getOrDefault("base64", "")).replaceAll("^data:[^,]*,", "").trim();
        String mime = String.valueOf(req.getOrDefault("mime", "image/png"));
        if (!mime.equals("image/png") && !mime.equals("image/jpeg")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El logo debe ser PNG o JPG");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Imagen no válida");
        }
        if (bytes.length == 0 || bytes.length > 2 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El logo debe pesar hasta 2 MB");
        }
        Empresa e = service.get();
        e.setLogoBase64(b64);
        e.setLogoMime(mime);
        repo.save(e);
        bitacora.registrar(user, null, "empresa", "Cambió el logo de la empresa");
        return get();
    }

    /** «Restablecer IDBI»: vuelve al nombre y logo por defecto. */
    @PostMapping("/restablecer")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public Map<String, Object> restablecer(@AuthenticationPrincipal AuthenticatedUser user) {
        Empresa e = service.get();
        e.setNombre(EmpresaService.NOMBRE_POR_DEFECTO);
        e.setLogoBase64(null);
        e.setLogoMime(null);
        repo.save(e);
        bitacora.registrar(user, null, "empresa", "Restableció el nombre y el logo de la empresa");
        return get();
    }

    /**
     * Pasar a producción (irreversible, solo administrador). Las minutas existentes quedan como etapa demo: se conservan las Completas,
     * En revisión y Validadas (visibles, no cuentan) y dejan de verse las demás. Reportes, indicadores y panel empiezan en 0.
     */
    @PostMapping("/produccion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Transactional
    public Map<String, Object> produccion(@AuthenticationPrincipal AuthenticatedUser user) {
        Empresa e = service.get();
        if (e.getProduccionDesde() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La empresa ya está en producción");
        }
        Set<Long> visibles = new HashSet<>();
        for (Minuta m : minutas.findAll()) {
            boolean ok = EmpresaService.sobreviveAProduccion(m);
            m.setEtapa("demo");
            m.setVisible(ok);
            if (ok) {
                visibles.add(m.getId());
            }
            minutas.save(m);
        }
        // Notificaciones: solo quedan las de minutas visibles; las que no son de minutas se borran.
        List<UserNotification> borrar = notificaciones.findAll().stream().filter(n -> n.getMinutaId() == null || !visibles.contains(n.getMinutaId())).toList();
        notificaciones.deleteAll(borrar);
        e.setProduccionDesde(LocalDateTime.now());
        e.setProduccionPor(user.fullName());
        repo.save(e);
        bitacora.registrar(user, null, "empresa", "Pasó la empresa a producción");
        return Map.of("produccionDesde", e.getProduccionDesde().format(DMY));
    }

    @PutMapping("/copia-seguridad")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public Map<String, Object> copia(@RequestBody Map<String, Object> req, @AuthenticationPrincipal AuthenticatedUser user) {
        String f = String.valueOf(req.getOrDefault("frecuencia", "off"));
        if (!List.of("off", "diaria", "semanal", "mensual").contains(f)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Frecuencia no válida");
        }
        Empresa e = service.get();
        e.setCopiaFrecuencia(f);
        if (req.get("hora") instanceof Number h && h.intValue() >= 0 && h.intValue() <= 23) {
            e.setCopiaHora(h.intValue());
        }
        if (req.get("correo") != null) {
            String c = String.valueOf(req.get("correo")).trim();
            if (!c.isEmpty() && !c.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El correo no es válido");
            }
            e.setCopiaCorreo(c);
        }
        if (req.get("incluyeFotos") instanceof Boolean b) {
            e.setCopiaIncluyeFotos(b);
        }
        repo.save(e);
        bitacora.registrar(user, null, "copia", "Configuró la copia de seguridad: " + f);
        return get();
    }
}
