package com.upc.idbi.gateway.auth;

import com.upc.idbi.gateway.bitacora.BitacoraService;
import com.upc.idbi.gateway.notification.UserNotification;
import com.upc.idbi.gateway.notification.UserNotificationRepository;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Recuperar contraseña con aprobación del administrador (doc 0.0.7 §36 y §15).
 * Público: el técnico pide la solicitud (recibe 3 números) y responde con el que le dijo el administrador.
 * Administrador: ve el número, rechaza la solicitud o la lista en Equipo y accesos. El número nunca viaja al técnico.
 */
@RestController
@RequiredArgsConstructor
public class RecuperacionController {

    private static final int MINUTOS = 10;
    private static final SecureRandom RND = new SecureRandom();

    private final UserRepository users;
    private final SolicitudRecuperacionRepository solicitudes;
    private final UserNotificationRepository notificaciones;
    private final BitacoraService bitacora;

    private static String dos(int n) {
        return String.format("%02d", n);
    }

    private static List<String> tresNumeros(String correcto) {
        Set<String> s = new LinkedHashSet<>();
        s.add(correcto);
        while (s.size() < 3) {
            s.add(dos(10 + RND.nextInt(90)));
        }
        List<String> l = new ArrayList<>(s);
        Collections.shuffle(l, RND);
        return l;
    }

    /** El técnico escribe su correo. No se revela si la cuenta existe: siempre devuelve 3 números. */
    @PostMapping("/api/auth/recuperacion")
    @Transactional
    public Map<String, Object> solicitar(@RequestBody Map<String, Object> req) {
        String email = String.valueOf(req.getOrDefault("email", "")).trim().toLowerCase(Locale.ROOT);
        Optional<UserEntity> u = users.findByEmailIgnoreCase(email).filter(x -> x.getRole() != Role.ADMINISTRADOR);
        if (u.isEmpty()) {
            return Map.of("solicitudId", -1, "opciones", tresNumeros(dos(10 + RND.nextInt(90))), "venceEnMinutos", MINUTOS);
        }
        UserEntity user = u.get();
        solicitudes.findByUserIdAndEstado(user.getId(), "esperando").forEach(x -> {
            x.setEstado("rechazada");
            solicitudes.save(x);
        });
        String numero = dos(10 + RND.nextInt(90));
        LocalDateTime ahora = LocalDateTime.now();
        SolicitudRecuperacion s = solicitudes.save(SolicitudRecuperacion.builder().userId(user.getId()).email(user.getEmail()).nombre(user.getFullName())
                .rol(user.getRole().name()).numero(numero).estado("esperando").creadaEn(ahora).venceEn(ahora.plusMinutes(MINUTOS)).build());
        for (UserEntity admin : users.findByRole(Role.ADMINISTRADOR)) {
            notificaciones.save(UserNotification.builder().userId(admin.getId()).title("Cambio de contraseña")
                    .body(user.getFullName() + " quiere restablecer su contraseña. Ábrela para ver el código.").type("RECUPERACION").refId(s.getId()).createdAt(ahora).build());
        }
        return Map.of("solicitudId", s.getId(), "opciones", tresNumeros(numero), "venceEnMinutos", MINUTOS);
    }

    /** El técnico elige un número. Si coincide, se aprueba y se habilita el código para crear la nueva contraseña; si no, se rechaza. */
    @PostMapping("/api/auth/recuperacion/{id}/respuesta")
    @Transactional
    public Map<String, Object> respuesta(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        SolicitudRecuperacion s = id == null || id < 0 ? null : solicitudes.findById(id).orElse(null);
        String numero = String.valueOf(req.getOrDefault("numero", "")).trim();
        if (s == null || !"esperando".equals(s.getEstado()) || s.getVenceEn().isBefore(LocalDateTime.now())) {
            if (s != null && "esperando".equals(s.getEstado())) {
                s.setEstado("rechazada");
                solicitudes.save(s);
            }
            return Map.of("resultado", "rechazada");
        }
        if (!s.getNumero().equals(numero)) {
            s.setEstado("rechazada");
            solicitudes.save(s);
            return Map.of("resultado", "rechazada");
        }
        s.setEstado("aprobada");
        solicitudes.save(s);
        UserEntity user = users.findById(s.getUserId()).orElseThrow();
        String codigo = String.valueOf(100000 + RND.nextInt(900000));
        user.setResetCode(codigo);
        user.setResetCodeExpiresAt(LocalDateTime.now().plusMinutes(MINUTOS));
        users.save(user);
        bitacora.registrar(null, null, "aviso", "Aprobó el cambio de contraseña de " + user.getFullName());
        return Map.of("resultado", "aprobada", "email", user.getEmail(), "codigo", codigo);
    }

    // ---- Administrador ----

    @GetMapping("/api/recuperaciones")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public List<Map<String, Object>> lista() {
        LocalDateTime ahora = LocalDateTime.now();
        return solicitudes.findByEstadoOrderByCreadaEnDesc("esperando").stream().filter(s -> s.getVenceEn().isAfter(ahora)).map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("nombre", s.getNombre());
            m.put("rol", s.getRol());
            m.put("minutos", Math.max(0, Duration.between(ahora, s.getVenceEn()).toMinutes()));
            return m;
        }).toList();
    }

    /** Número de 2 dígitos para decírselo a la persona (en persona o por llamada, nunca por chat). */
    @GetMapping("/api/recuperaciones/{id}/codigo")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public Map<String, Object> codigo(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser admin) {
        SolicitudRecuperacion s = solicitudes.findById(id).orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada"));
        if (!"esperando".equals(s.getEstado()) || s.getVenceEn().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("La solicitud ya no está vigente");
        }
        bitacora.registrar(admin, null, "aviso", "Vio el código de cambio de contraseña de " + s.getNombre());
        return Map.of("nombre", s.getNombre(), "numero", s.getNumero(), "minutos", Math.max(0, Duration.between(LocalDateTime.now(), s.getVenceEn()).toMinutes()));
    }

    /** «No reconozco esta solicitud» o «Anular». */
    @PostMapping("/api/recuperaciones/{id}/rechazar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Transactional
    public Map<String, Object> rechazar(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser admin) {
        SolicitudRecuperacion s = solicitudes.findById(id).orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada"));
        s.setEstado("rechazada");
        solicitudes.save(s);
        bitacora.registrar(admin, null, "aviso", "Rechazó el cambio de contraseña de " + s.getNombre());
        return Map.of("ok", true);
    }
}
