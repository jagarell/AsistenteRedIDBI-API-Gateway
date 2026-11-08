package com.upc.idbi.gateway.roles;

import com.upc.idbi.gateway.auth.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Roles y permisos (web y app del administrador) y el perfil de Reportes de cada rol. */
@Service
@RequiredArgsConstructor
public class RoleService {

    public static final List<String> ALL = List.of("m_own", "m_drafts", "m_all", "m_validated", "mod_panel", "mod_notif", "mod_rep", "mod_equipo",
            "mod_preg", "mod_plan", "a_create", "a_validate", "a_reject", "a_annul", "a_map", "a_codes", "n_own", "n_team", "r_tec_all", "r_tec_mine");
    /** Casillas que no se pueden quitar a cada rol base. */
    static final Map<String, Set<String>> LOCKED = Map.of(
            "admin", Set.copyOf(ALL),
            "sup", Set.of("a_validate", "a_reject", "n_own", "mod_notif"),
            "tec", Set.of("m_own", "a_create", "n_own"));
    private static final Map<String, String> EXCLUSIVE = Map.of("r_tec_all", "r_tec_mine", "r_tec_mine", "r_tec_all");

    private final RolePermissionRepository repository;

    public static List<RolePermission> defaults() {
        Set<String> admin = new LinkedHashSet<>(ALL);
        admin.remove("n_own");
        admin.remove("r_tec_mine");
        List<RolePermission> l = new ArrayList<>();
        l.add(RolePermission.builder().id("admin").name("Administrador").kind("fijo").perms(String.join(",", admin))
                .description("Dueño de la cuenta de la empresa. Agrega personas, define roles, entrega los códigos de contraseña y configura las preguntas.").build());
        l.add(RolePermission.builder().id("sup").name("Supervisor").kind("base")
                .perms("m_own,m_drafts,m_validated,mod_panel,mod_notif,mod_rep,a_create,a_validate,a_reject,a_map,n_own,r_tec_mine")
                .description("Valida o devuelve a revisión las minutas de los técnicos a su cargo.").build());
        l.add(RolePermission.builder().id("tec").name("Técnico").kind("base").perms("m_own,mod_notif,mod_rep,a_create,a_annul,a_map,n_own")
                .description("Hace las visitas con el asistente y envía sus minutas a su supervisor.").build());
        l.add(RolePermission.builder().id("lect").name("Solo consulta").kind("propio").perms("m_validated")
                .description("Rol creado por la empresa: solo consulta el historial de minutas validadas.").build());
        return l;
    }

    @Transactional
    public List<RolePermission> list() {
        if (repository.count() == 0) {
            repository.saveAll(defaults());
        }
        List<RolePermission> all = repository.findAll();
        // El rol de Supervisor siempre debe poder validar y devolver minutas (y el de Técnico crear las suyas): si una base
        // antigua quedó sin esas casillas bloqueadas, se restituyen aquí.
        for (RolePermission r : all) {
            Set<String> locked = LOCKED.get(r.getId());
            if (locked == null || "admin".equals(r.getId())) {
                continue;
            }
            Set<String> p = new LinkedHashSet<>(r.permSet());
            if (p.addAll(locked)) {
                r.setPermSet(p);
                repository.save(r);
            }
        }
        return all;
    }

    public static String idOf(Role role) {
        return role == Role.ADMINISTRADOR ? "admin" : role == Role.SUPERVISOR ? "sup" : "tec";
    }

    @Transactional
    public RolePermission forRole(Role role) {
        list();
        return repository.findById(idOf(role)).orElseThrow();
    }

    /** Pestañas de Reportes del rol: General (m_all), Por técnico (r_tec_all) o Mis técnicos (r_tec_mine), Mis minutas (a_create). */
    public static ReportProfile profile(RolePermission r) {
        Set<String> p = r.permSet();
        String tecs = p.contains("r_tec_all") ? "all" : p.contains("r_tec_mine") ? "mine" : "none";
        return new ReportProfile(p.contains("m_all"), tecs, p.contains("a_create"));
    }

    /** Cambia permisos respetando las casillas bloqueadas y la exclusión Por técnico / Mis técnicos. */
    @Transactional
    public RolePermission update(String id, String name, Set<String> perms) {
        list();
        RolePermission r = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Rol no encontrado"));
        Set<String> locked = LOCKED.getOrDefault(id, Set.of());
        Set<String> next = new LinkedHashSet<>();
        for (String k : perms) {
            if (ALL.contains(k)) {
                next.add(k);
            }
        }
        for (String k : locked) {
            if (r.permSet().contains(k)) {
                next.add(k);
            } else if (!"admin".equals(id)) {
                next.remove(k);
            }
        }
        if ("admin".equals(id)) {
            next = r.permSet();
        }
        if (next.contains("r_tec_all") && next.contains("r_tec_mine")) {
            next.remove(r.permSet().contains("r_tec_all") ? "r_tec_all" : "r_tec_mine");
        }
        r.setPermSet(next);
        if ("propio".equals(r.getKind()) && name != null && !name.isBlank()) {
            r.setName(name.trim());
        }
        return repository.save(r);
    }

    @Transactional
    public RolePermission create(String name, Set<String> perms) {
        list();
        int n = 1;
        while (repository.existsById("custom" + n)) {
            n++;
        }
        Set<String> clean = new LinkedHashSet<>();
        for (String k : perms) {
            if (ALL.contains(k)) {
                clean.add(k);
            }
        }
        for (Map.Entry<String, String> e : EXCLUSIVE.entrySet()) {
            if (clean.contains(e.getKey()) && clean.contains(e.getValue())) {
                clean.remove(e.getValue());
            }
        }
        RolePermission r = RolePermission.builder().id("custom" + n).name(name == null || name.isBlank() ? "Rol nuevo " + n : name.trim())
                .kind("propio").description("Rol creado por la empresa.").perms(String.join(",", clean)).build();
        return repository.save(r);
    }

    public record ReportProfile(boolean all, String tecs, boolean own) {
    }
}
