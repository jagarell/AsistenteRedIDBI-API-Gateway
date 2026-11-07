package com.upc.idbi.gateway.asistente;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.idbi.gateway.auth.Role;
import com.upc.idbi.gateway.auth.UserEntity;
import com.upc.idbi.gateway.auth.UserRepository;
import com.upc.idbi.gateway.common.FastApiClient;
import com.upc.idbi.gateway.conocimiento.AprendizajeService;
import com.upc.idbi.gateway.conocimiento.FichaConocimientoRepository;
import com.upc.idbi.gateway.conocimiento.ReglaEmpresa;
import com.upc.idbi.gateway.conocimiento.ReglaEmpresaRepository;
import com.upc.idbi.gateway.minuta.Minuta;
import com.upc.idbi.gateway.minuta.MinutaChangesService;
import com.upc.idbi.gateway.minuta.MinutaRepository;
import com.upc.idbi.gateway.minuta.MinutaStatus;
import com.upc.idbi.gateway.minuta.MinutaVersionRepository;
import com.upc.idbi.gateway.reportes.ReportesService;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Asistente IA 0.0.8 (doc 01): conversación libre sin botones de opciones. Decide la intención del mensaje y responde con
 * <b>bloques</b> que la app dibuja como tarjetas: borrador de minuta, actualizar minuta, consulta a la base, pendientes del día,
 * minutas por validar, reportes y respuestas de ChatGPT. Las acciones que cambian datos (redactar, actualizar) no se ejecutan aquí:
 * se devuelve una tarjeta y el usuario confirma con un botón.
 */
@Service
@RequiredArgsConstructor
public class AsistenteChatService {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern UPD = Pattern.compile("\\b(actualiz|modific|corrig|cambi)\\w*\\b[^.]*\\bminuta", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUEVA = Pattern.compile("\\b(redact|gener|cre[ae]|arm[ae]|haz|hacer|elabor)\\w*\\b[^.]*\\bminuta|\\bnueva minuta", Pattern.CASE_INSENSITIVE);
    private static final Pattern POR_VALIDAR = Pattern.compile("por validar|pendientes? de validaci|falta(n)? validar|sin validar", Pattern.CASE_INSENSITIVE);
    private static final Pattern PENDIENTES = Pattern.compile("pendientes?|qu[eé] me falta|qu[eé] tengo", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPORTE = Pattern.compile("\\b(reporte|informe|eficiencia|productividad)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONSULTA = Pattern.compile("fecha|cu[aá]ndo|observaci|recomendaci|historial|estado|t[eé]cnico|versi[oó]n|qu[eé] (se )?(vio|encontr)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEGUIMIENTO = Pattern.compile("^(solo|sin|no de|de |del |ahora|tambien|ademas|excepto|menos)|mes anterior|mes pasado");
    private static final Set<String> VACIAS = Set.of("ejemplo", "local", "restaurante", "cocina", "minuta", "cliente", "grupo");

    private final MinutaRepository minutas;
    private final MinutaVersionRepository versions;
    private final MinutaChangesService changes;
    private final UserRepository users;
    private final FastApiClient fastApi;
    private final ReglaEmpresaRepository reglas;
    private final FichaConocimientoRepository fichas;
    private final AprendizajeService aprendizaje;
    private final ObjectMapper mapper;
    private final com.upc.idbi.gateway.bitacora.BitacoraService bitacora;
    private final com.upc.idbi.gateway.empresa.EmpresaService empresa;
    private final ReporteProgramadoRepository programados;
    private final com.upc.idbi.gateway.notification.UserNotificationRepository notificaciones;

    // ---------------------------------------------------------------- entrada

    public Map<String, Object> mensaje(Map<String, Object> req, AuthenticatedUser user) {
        String texto = str(req.get("texto")).trim();
        String modo = str(req.get("modo"));
        String tipo = str(req.get("tipo"));
        Long minutaId = num(req.get("minutaId"));
        List<String> adjuntos = strList(req.get("adjuntos"));
        if (texto.isBlank() && adjuntos.isEmpty()) {
            return out(texto("Cuéntame qué necesitas o adjunta las evidencias."));
        }
        List<Minuta> visibles = visibles(user);

        if ("upd".equals(modo) && minutaId != null) {
            return actualizar(minutaId, texto, user);
        }
        if ("new".equals(modo) || (modo.isBlank() && !UPD.matcher(texto).find()
                && (NUEVA.matcher(texto).find() || !adjuntos.isEmpty() || esContextoDeVisita(texto)))) {
            return nueva(texto, adjuntos, tipo, req);
        }
        if (UPD.matcher(texto).find()) {
            Match m = buscar(texto, visibles);
            if (m == null) {
                return out(texto("¿De qué minuta? Dime el cliente y la fecha, por ejemplo: «Actualiza la minuta de Restobar Larco del 10/10/2026: …»."),
                        recientes(visibles, 4));
            }
            String cambios = cambios(texto);
            Map<String, Object> aviso = m.fechaNoCoincide == null ? null
                    : texto("Ojo: la fecha " + m.fechaNoCoincide + " no coincide. La minuta de " + m.minuta.getClientName() + " es del " + fecha(m.minuta) + "; actualizo esa.");
            if (cambios.isBlank()) {
                Map<String, Object> r = out(texto("Encontré la minuta de " + m.minuta.getClientName() + " del " + fecha(m.minuta) + " (" + etiqueta(m.minuta)
                        + ", v" + m.minuta.versionOrOne() + "). ¿Qué hay que actualizar? Cuéntamelo por texto o audio y adjunta evidencias si las tienes."));
                r.put("estado", Map.of("modo", "upd", "minutaId", m.minuta.getId()));
                return r;
            }
            Map<String, Object> r = actualizar(m.minuta.getId(), cambios, user);
            if (aviso != null) {
                bloques(r).add(0, aviso);
            }
            return r;
        }
        if (req.get("reporte") instanceof Map<?, ?> prev && !prev.isEmpty() && SEGUIMIENTO.matcher(norm(texto)).find()) {
            return reporte(texto, req, user, visibles);
        }
        if (POR_VALIDAR.matcher(texto).find()) {
            return porValidar(user, visibles);
        }
        if (REPORTE.matcher(texto).find()) {
            return reporte(texto, req, user, visibles);
        }
        if (PENDIENTES.matcher(texto).find()) {
            return pendientes(user, visibles);
        }
        Match m = buscar(texto, visibles);
        if (m != null && CONSULTA.matcher(texto).find()) {
            return consulta(m.minuta, user);
        }
        return libre(texto, user, visibles);
    }

    // ---------------------------------------------------------------- minuta nueva

    private static final Pattern PISTA_VISITA = Pattern.compile("raz[oó]n social|\\bruc\\b|se llama|encargad|conectad|impresora|router|switch|repetidor|direcci[oó]n", Pattern.CASE_INSENSITIVE);

    /** El técnico pegó o dictó el contexto de una visita sin decir «redacta»: si trae varios datos del local o de la red, es una minuta nueva. */
    private static boolean esContextoDeVisita(String texto) {
        if (texto.length() < 60 || texto.contains("?")) {
            return false;
        }
        Matcher m = PISTA_VISITA.matcher(texto);
        Set<String> vistas = new HashSet<>();
        while (m.find()) {
            vistas.add(m.group().toLowerCase());
        }
        return vistas.size() >= 3;
    }

    private Map<String, Object> nueva(String texto, List<String> adjuntos, String tipo, Map<String, Object> req) {
        boolean soporte = Pattern.compile("soporte|incidencia", Pattern.CASE_INSENSITIVE).matcher(texto).find() || "sop".equals(tipo);
        if (soporte) {
            Map<String, Object> r = out(texto("¿De qué cliente es la visita de soporte? Escribe el nombre del local o la razón social."));
            r.put("estado", Map.of("modo", "soporte"));
            return r;
        }
        boolean dijoImp = Pattern.compile("implementaci", Pattern.CASE_INSENSITIVE).matcher(texto).find();
        if (tipo.isBlank() && !dijoImp) {
            Map<String, Object> b = bloque("tipo_minuta", "Nueva minuta", "¿Qué minuta vas a redactar?");
            b.put("badge", "Tipo");
            b.put("acciones", List.of(accion("tipo_imp", "Implementación", true, Map.of("tipo", "imp", "texto", texto)),
                    accion("tipo_sop", "Soporte / incidencia", true, Map.of("tipo", "sop", "texto", texto))));
            return out(b);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("texto", texto);
        body.put("adjuntos", adjuntos);
        Map<String, Object> ex = fastApi.post("/asistente/extraer", body);
        Map<String, Object> b = bloque("borrador_minuta", "Borrador de minuta", "Esto es lo que entendí. Revísalo antes de redactar.");
        @SuppressWarnings("unchecked") Map<String, Object> d = (Map<String, Object>) ex.getOrDefault("datos", Map.of());
        List<List<String>> filas = new ArrayList<>();
        filas.add(fila("Local", d.get("local")));
        filas.add(fila("Razón social", d.get("razon")));
        filas.add(fila("RUC", d.get("ruc")));
        filas.add(fila("Dirección", d.get("direccion")));
        filas.add(fila("Encargado", d.get("encargado")));
        filas.add(fila("Celular", d.get("celular")));
        filas.add(fila("Correo", d.get("correo")));
        filas.add(fila("Rubro", d.get("rubro")));
        filas.add(fila("Estado del local", d.get("estado")));
        filas.add(fila("Áreas de preparación", d.get("areas") instanceof List<?> l ? String.join(", ", l.stream().map(String::valueOf).toList()) : null));
        filas.add(fila("Internet", d.get("internet")));
        List<?> con = ex.get("conexiones") instanceof List<?> l ? l : List.of();
        filas.add(fila("Conexiones de red", con.isEmpty() ? null : con.size() + " enlace" + (con.size() == 1 ? "" : "s")));
        b.put("filas", filas);
        List<String> lineas = new ArrayList<>();
        for (Object c : con) {
            Map<?, ?> cm = (Map<?, ?>) c;
            lineas.add(cm.get("desde") + " → " + cm.get("hacia") + " · " + cm.get("medio"));
        }
        b.put("lineas", lineas);
        int nObs = size(ex.get("observaciones")), nRec = size(ex.get("recomendaciones")), nPaso = size(ex.get("pasos"));
        filas.add(fila("Contenido", nObs + " observaciones · " + nRec + " recomendaciones · " + nPaso + " pasos a seguir"));
        List<?> evs = ex.get("evidencias") instanceof List<?> l ? l : List.of();
        filas.add(fila("Evidencias", evs.size() + (evs.size() == 1 ? " foto" : " fotos")));
        List<String> avisos = new ArrayList<>();
        if (!evs.isEmpty() || !adjuntos.isEmpty()) {
            avisos.add("Evidencias recibidas: " + String.join(", ", adjuntos) + ".");
        }
        List<?> faltan = ex.get("faltan") instanceof List<?> l ? l : List.of();
        if (!faltan.isEmpty()) {
            avisos.add("Faltan evidencias: " + faltan.stream().map(String::valueOf).collect(Collectors.joining(", "))
                    + ". Quedan como «Subir después» y se piden antes de solicitar la validación.");
        }
        if (d.get("local") == null && d.get("razon") == null) {
            avisos.add("No encontré el nombre del local ni la razón social. Puedes dármelos ahora o completarlos luego en Editar › Datos del local.");
        }
        b.put("avisos", avisos);
        b.put("datos", ex);
        b.put("acciones", List.of(accion("agregar", "Agregar más", false, Map.of()), accion("redactar", "Redactar minuta", true, Map.of())));
        Map<String, Object> r = out(b);
        if (Boolean.TRUE.equals(ex.get("pideConexiones"))) {
            bloques(r).add(texto("Cuéntame por audio o texto cómo están conectados el router, el switch, los repetidores, las PC y las impresoras, así dibujo la topología."));
        }
        r.put("estado", Map.of("modo", "new", "tipo", "imp"));
        return r;
    }

    /** «Redactar minuta»: crea la minuta con lo entendido. Queda sin terminar (Borrador) hasta que el técnico la revise y solicite la validación. */
    public Map<String, Object> redactar(Map<String, Object> req, AuthenticatedUser user) {
        @SuppressWarnings("unchecked") Map<String, Object> ex = (Map<String, Object>) req.getOrDefault("datos", Map.of());
        @SuppressWarnings("unchecked") Map<String, Object> d = ex.get("datos") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        String local = firstNonBlank(str(d.get("local")), str(d.get("razon")), "Local sin nombre");
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("origen", "ASISTENTE");
        content.put("datos", d);
        content.put("obsTec", String.join(" ", strList(ex.get("observaciones"))));
        List<Map<String, String>> recs = new ArrayList<>();
        for (String t : strList(ex.get("recomendaciones"))) {
            recs.add(Map.of("t", t, "d", ""));
        }
        content.put("recs", recs);
        content.put("pasos", ex.getOrDefault("pasos", List.of()));
        content.put("conexiones", ex.getOrDefault("conexiones", List.of()));
        content.put("evidenciasFaltantes", ex.getOrDefault("faltan", List.of()));
        LocalDateTime now = LocalDateTime.now();
        Minuta m = minutas.save(Minuta.builder()
                .clientName(local).address(str(d.get("direccion")))
                .contactName(str(d.get("encargado"))).contactPhone(str(d.get("celular")))
                .technicianId(user.id()).technicianName(user.fullName()).status(MinutaStatus.BORRADOR)
                .tipo("IMPLEMENTACION").version(1).rubro(str(d.get("rubro")))
                .summary("Minuta redactada con el Asistente IA.")
                .topologyJson(topologia(ex.get("conexiones")))
                .contentJson(write(content)).etapa(empresa.enProduccion() ? "produccion" : null).createdAt(now).updatedAt(now).build());
        bitacora.registrar(user, m.getId(), "crear", "Redactó la minuta de " + local + " con el Asistente IA");
        return out(texto("Listo: redacté la minuta de " + local + " (Pendiente). Te llevo a Ver minuta para que la revises."),
                accionBloque("abrir_minuta", m.getId()));
    }

    private String topologia(Object conexiones) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> links = new ArrayList<>();
        Map<String, String> ids = new LinkedHashMap<>();
        if (conexiones instanceof List<?> l) {
            for (Object o : l) {
                Map<?, ?> c = (Map<?, ?>) o;
                for (String k : new String[]{"desde", "hacia"}) {
                    String n = String.valueOf(c.get(k));
                    if (!ids.containsKey(n)) {
                        ids.put(n, "n" + (ids.size() + 1));
                        nodes.add(Map.of("id", ids.get(n), "label", n, "type", tipoNodo(n)));
                    }
                }
                links.add(Map.of("from", ids.get(String.valueOf(c.get("desde"))), "to", ids.get(String.valueOf(c.get("hacia"))),
                        "medium", String.valueOf(c.get("medio")).toLowerCase()));
            }
        }
        return nodes.isEmpty() ? null : write(Map.of("nodes", nodes, "links", links));
    }

    private static String tipoNodo(String n) {
        String s = norm(n);
        return s.startsWith("imp") ? "PRINTER" : s.startsWith("pc") ? "PC" : s.startsWith("router") ? "ROUTER" : s.startsWith("switch") ? "SWITCH"
                : s.startsWith("repetidor") ? "REPEATER" : "DEVICE";
    }

    // ---------------------------------------------------------------- actualizar

    private Map<String, Object> actualizar(Long id, String texto, AuthenticatedUser user) {
        Minuta m = minutas.findById(id).orElse(null);
        if (m == null) {
            return out(texto("No encontré esa minuta."));
        }
        Map<String, Object> plan = changes.plan(id, texto);
        if (Boolean.TRUE.equals(plan.get("criterio"))) {
            return out(texto(str(plan.get("mensaje"))));
        }
        List<?> items = plan.get("items") instanceof List<?> l ? l : List.of();
        if (items.isEmpty()) {
            return out(texto("No encontré cambios para aplicar. Cuéntame qué cambió en la visita."));
        }
        Map<String, Object> b = bloque("actualizar_minuta", "Actualizar minuta · " + m.getClientName(),
                "Esto es lo que cambiaré. Nada se guarda hasta que confirmes.");
        b.put("badge", "v" + m.versionOrOne() + " → v" + (m.versionOrOne() + 1));
        List<String> lineas = new ArrayList<>();
        for (Object o : items) {
            Map<?, ?> it = (Map<?, ?>) o;
            lineas.add(rotulo(str(it.get("k"))) + ": " + str(it.get("t")));
        }
        b.put("lineas", lineas);
        b.put("minutaId", id);
        b.put("items", items);
        boolean obras = Pattern.compile("instal|cambi|mov|reubic|conect|arregl|obra|visit|fui|estuve|se dej", Pattern.CASE_INSENSITIVE).matcher(texto).find();
        b.put("nuevaVisita", obras);
        b.put("nuevaVisitaTexto", "Fue una nueva visita al local");
        List<String> avisos = new ArrayList<>();
        if (m.getStatus() == MinutaStatus.COMPLETA || m.getStatus() == MinutaStatus.VALIDADA) {
            avisos.add("La minuta estaba " + (m.getStatus() == MinutaStatus.COMPLETA ? "Completa" : "Validada")
                    + ": al aplicar vuelve a Pendiente y pasa otra vez por la validación del supervisor.");
        }
        b.put("avisos", avisos);
        b.put("acciones", List.of(accion("cancelar", "Cancelar", false, Map.of()), accion("aplicar", "Aplicar y ver minuta", true, Map.of("minutaId", id))));
        return out(b);
    }

    private static String rotulo(String k) {
        return switch (k) {
            case "router" -> "Ubicación del router";
            case "swport" -> "Puertos del switch";
            case "cred" -> "Acceso";
            case "rec" -> "Recomendación";
            case "pasoC" -> "Paso del cliente";
            case "pasoT" -> "Paso del equipo técnico";
            case "obs" -> "Observación";
            default -> "Cambio";
        };
    }

    // ---------------------------------------------------------------- consultas y gestión

    private Map<String, Object> consulta(Minuta m, AuthenticatedUser user) {
        boolean detalle = puedeVerDetalle(m, user);
        Map<String, Object> b = bloque("consulta_minuta", "Base de datos · " + m.getClientName(), null);
        b.put("badge", "Minuta v" + m.versionOrOne());
        List<List<String>> f = new ArrayList<>();
        f.add(fila("Fecha de visita", fecha(m)));
        f.add(fila("Técnico", m.getTechnicianName()));
        f.add(fila("Estado", etiqueta(m)));
        if (detalle) {
            Map<String, Object> c = read(m.getContentJson());
            String obs = str(c.get("obsTec"));
            f.add(fila("Observaciones", obs.isBlank() ? "Sin observaciones" : obs));
            List<String> recs = new ArrayList<>();
            if (c.get("recs") instanceof List<?> l) {
                for (Object o : l) {
                    recs.add(o instanceof Map<?, ?> mm ? str(mm.get("t")) : String.valueOf(o));
                }
            }
            f.add(fila("Recomendaciones", recs.isEmpty() ? "Sin recomendaciones" : String.join(" · ", recs)));
            var hist = versions.findByMinutaIdOrderByVersionDesc(m.getId());
            f.add(fila("Historial", hist.isEmpty() ? "Sin actualizaciones" : hist.size() + " actualización(es); la última: " + firstLine(hist.get(0).getResumen())));
        } else {
            b.put("avisos", List.of("Es una minuta de otro técnico: solo ves fecha, técnico y estado."));
        }
        b.put("filas", f);
        b.put("acciones", List.of(accion("abrir_minuta", "Ver minuta", false, Map.of("minutaId", m.getId()))));
        return out(b);
    }

    private Map<String, Object> pendientes(AuthenticatedUser user, List<Minuta> visibles) {
        List<Minuta> mias = alcance(user, visibles);
        List<Minuta> sinTerminar = mias.stream().filter(m -> m.getStatus() == MinutaStatus.BORRADOR && m.getRejectionReason() == null).toList();
        List<Minuta> rechazadas = mias.stream().filter(m -> m.getStatus() == MinutaStatus.BORRADOR && m.getRejectionReason() != null).toList();
        List<Minuta> validar = mias.stream().filter(m -> m.getStatus() == MinutaStatus.COMPLETA).toList();
        Map<String, Object> b = bloque("pendientes", "Tus pendientes del día", null);
        List<Map<String, Object>> items = new ArrayList<>();
        boolean tec = user.role() == Role.TECNICO;
        sinTerminar.forEach(m -> items.add(item(m, tec ? "Sin terminar · continúa en el Chat IA" : "Sin terminar · " + m.getTechnicianName())));
        rechazadas.forEach(m -> items.add(item(m, "Rechazada: " + firstNonBlank(m.getRejectionReason(), "corrígela y vuelve a enviarla"))));
        if (!tec) {
            validar.forEach(m -> items.add(item(m, "Por validar · " + m.getTechnicianName())));
        }
        if (items.isEmpty()) {
            return out(texto("No tienes pendientes. ¡Todo al día!"));
        }
        b.put("items", items);
        b.put("texto", tec ? sinTerminar.size() + " sin terminar, " + rechazadas.size() + " rechazadas."
                : sinTerminar.size() + " sin terminar, " + rechazadas.size() + " rechazadas, " + validar.size() + " por validar.");
        return out(b);
    }

    private Map<String, Object> porValidar(AuthenticatedUser user, List<Minuta> visibles) {
        List<Minuta> validar = alcance(user, visibles).stream().filter(m -> m.getStatus() == MinutaStatus.COMPLETA).toList();
        if (validar.isEmpty()) {
            return out(texto(user.role() == Role.TECNICO ? "Ninguna de tus minutas espera validación." : "No hay minutas por validar."));
        }
        Map<String, Object> b = bloque("por_validar", "Minutas por validar", validar.size() + " esperando validación.");
        b.put("items", validar.stream().map(m -> item(m, "Completa · " + m.getTechnicianName() + " · " + fecha(m))).toList());
        return out(b);
    }

    // ---------------------------------------------------------------- reportes (doc 0.0.7 §50)

    private static final Map<String, List<String>> ESTADOS = new LinkedHashMap<>();

    static {
        ESTADOS.put("incomplet", List.of("Pendiente", "En revisión"));
        ESTADOS.put("borrador", List.of("Pendiente"));
        ESTADOS.put("pendiente", List.of("Pendiente"));
        ESTADOS.put("por validar", List.of("Completa"));
        ESTADOS.put("completa", List.of("Completa"));
        ESTADOS.put("en revision", List.of("En revisión"));
        ESTADOS.put("rechazad", List.of("En revisión"));
        ESTADOS.put("validada", List.of("Validada"));
    }

    private static final Pattern PROGRAMAR = Pattern.compile("cada fin de mes|fin de mes|todos los meses|cada mes|mensualmente");
    private static final String[] MESES = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};

    /** Interpreta el pedido de reporte y, si ya había uno, lo ajusta sin repetir todo («solo el mes anterior», «sin octubre», «de María»). */
    @SuppressWarnings("unchecked")
    private Map<String, Object> reporte(String texto, Map<String, Object> req, AuthenticatedUser user, List<Minuta> visibles) {
        Map<String, Object> c = new LinkedHashMap<>();
        boolean seguimiento = req.get("reporte") instanceof Map<?, ?> m && !m.isEmpty();
        if (seguimiento) {
            c.putAll((Map<String, Object>) req.get("reporte"));
        }
        String s = norm(texto).replace("setiembre", "septiembre");
        // periodo
        List<String> excluir = new ArrayList<>(c.get("excluir") instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of());
        Matcher neg = Pattern.compile("(?:sin|no de|excepto|menos|salvo)\\s+(?:el mes de\\s+|el mes\\s+|las de\\s+)?(" + String.join("|", MESES) + ")").matcher(s);
        boolean excluyo = false;
        while (neg.find()) {
            int i = Arrays.asList(MESES).indexOf(neg.group(1));
            excluir.add(java.time.YearMonth.of(LocalDate.now().getYear(), i + 1).toString());
            excluyo = true;
        }
        LocalDate[] r = excluyo ? null : periodoOpt(s);
        if (r != null) {
            c.put("desde", r[0].toString());
            c.put("hasta", r[1].toString());
            excluir.clear();
        }
        if (c.get("desde") == null) {
            LocalDate hoy = LocalDate.now();
            c.put("desde", hoy.withDayOfMonth(1).toString());
            c.put("hasta", hoy.toString());
        }
        c.put("excluir", excluir);
        // estado
        List<String> estados = null;
        for (var e : ESTADOS.entrySet()) {
            if (s.contains(e.getKey())) {
                estados = e.getValue();
                break;
            }
        }
        if (estados != null) {
            c.put("estados", estados);
            c.put("riesgo", false);
        }
        if (s.contains("en riesgo") || s.contains("riesgo")) {
            c.put("riesgo", true);
            c.put("estados", List.of());
        }
        // persona (técnico o supervisor)
        if (user.role() != Role.TECNICO) {
            String persona = personaDe(s);
            if (persona != null) {
                c.put("persona", persona);
            }
        }
        if (PROGRAMAR.matcher(s).find()) {
            // «Cada fin de mes»: el periodo sigue al mes en curso cuando se envíe.
            Map<String, Object> consulta = new LinkedHashMap<>(c);
            consulta.remove("desde");
            consulta.remove("hasta");
            consulta.put("excluir", List.of());
            ReporteProgramado p = programados.save(ReporteProgramado.builder().userId(user.id()).frecuencia("fin_de_mes").consultaJson(write(consulta))
                    .descripcion(descripcion(c).replaceFirst("^.*? · del \\d{2}/\\d{2}/\\d{4} al \\d{2}/\\d{2}/\\d{4}", "").replaceFirst("^ · ", "")).creadoEn(LocalDateTime.now()).build());
            Map<String, Object> b = bloque("reporte_programado", "Reporte programado", "Listo: cada fin de mes te enviaré este reporte por notificación"
                    + (p.getDescripcion().isBlank() ? "" : " (" + p.getDescripcion() + ")") + ". Puedes cancelarlo cuando quieras.");
            b.put("acciones", List.of(accion("cancelar_programado", "Cancelar el envío", false, Map.of("id", p.getId()))));
            bitacora.registrar(user, null, "aviso", "Programó un reporte de minutas cada fin de mes");
            return out(b);
        }
        List<Minuta> filas = filtrar(c, user, visibles);
        Map<String, Object> b = bloque("reporte", "Reporte · " + filas.size() + (filas.size() == 1 ? " minuta" : " minutas"),
                filas.isEmpty() ? "No encontré minutas con esos filtros." : (seguimiento ? "Actualicé el reporte: " : "") + descripcion(c) + ".");
        Map<String, Object> tabla = new LinkedHashMap<>();
        tabla.put("columnas", List.of("Local", "Técnico", "Supervisor", "Estado", "Puntaje", "Fecha"));
        Map<Long, String> sup = supervisores();
        tabla.put("filas", filas.stream().limit(40).map(m -> List.<Object>of(str(m.getClientName()), str(m.getTechnicianName()),
                sup.getOrDefault(m.getTechnicianId(), "—"), ReportesService.estadoDe(m), m.getPuntaje() == null ? "—" : m.getPuntaje() + " %", fecha(m))).toList());
        b.put("tabla", tabla);
        b.put("consulta", c);
        List<Map<String, Object>> acc = new ArrayList<>();
        if (!filas.isEmpty()) {
            acc.add(accion("descargar_reporte", "Descargar Excel", true, Map.of("consulta", c)));
        }
        acc.add(accion("ver_reportes", "Ver en Reportes", false, Map.of()));
        b.put("acciones", acc);
        if (filas.size() > 40) {
            b.put("avisos", List.of("Se muestran las primeras 40; el Excel trae las " + filas.size() + "."));
        }
        return out(b);
    }

    private String descripcion(Map<String, Object> c) {
        List<String> p = new ArrayList<>();
        if (c.get("riesgo") == Boolean.TRUE) {
            p.add("en riesgo (menos de 60 %, sin validar)");
        } else if (c.get("estados") instanceof List<?> l && !l.isEmpty()) {
            p.add(String.join(" y ", l.stream().map(x -> String.valueOf(x).toLowerCase()).toList()));
        }
        p.add("del " + LocalDate.parse(str(c.get("desde"))).format(DMY) + " al " + LocalDate.parse(str(c.get("hasta"))).format(DMY));
        if (c.get("excluir") instanceof List<?> l && !l.isEmpty()) {
            p.add("sin " + l.stream().map(x -> MESES[java.time.YearMonth.parse(String.valueOf(x)).getMonthValue() - 1]).collect(Collectors.joining(", ")));
        }
        if (!str(c.get("persona")).isBlank()) {
            p.add("de " + c.get("persona"));
        }
        return String.join(" · ", p);
    }

    /** Minutas del alcance del rol que cumplen la consulta (periodo, meses excluidos, estado, riesgo y persona). */
    @SuppressWarnings("unchecked")
    private List<Minuta> filtrar(Map<String, Object> c, AuthenticatedUser user, List<Minuta> visibles) {
        LocalDate d = LocalDate.parse(str(c.get("desde"))), h = LocalDate.parse(str(c.get("hasta")));
        Set<String> estados = c.get("estados") instanceof List<?> l ? l.stream().map(String::valueOf).collect(Collectors.toSet()) : Set.of();
        Set<String> excl = c.get("excluir") instanceof List<?> l ? l.stream().map(String::valueOf).collect(Collectors.toSet()) : Set.of();
        String persona = str(c.get("persona"));
        Set<Long> delEquipo = new HashSet<>();
        if (!persona.isBlank()) {
            users.findAll().stream().filter(u -> norm(u.getFullName()).equals(norm(persona))).findFirst().ifPresent(u -> {
                delEquipo.add(u.getId());
                if (u.getRole() == Role.SUPERVISOR) {
                    users.findByRole(Role.TECNICO).stream().filter(t -> Objects.equals(t.getSupervisorId(), u.getId())).forEach(t -> delEquipo.add(t.getId()));
                }
            });
        }
        return alcance(user, visibles).stream()
                .filter(m -> m.getCreatedAt() != null && !m.getCreatedAt().toLocalDate().isBefore(d) && !m.getCreatedAt().toLocalDate().isAfter(h))
                .filter(m -> !excl.contains(java.time.YearMonth.from(m.getCreatedAt()).toString()))
                .filter(m -> estados.isEmpty() || estados.contains(ReportesService.estadoDe(m)))
                .filter(m -> c.get("riesgo") != Boolean.TRUE || (!"Validada".equals(ReportesService.estadoDe(m)) && "Riesgo".equals(ReportesService.criticidadDe(m.getPuntaje()))))
                .filter(m -> persona.isBlank() || delEquipo.contains(m.getTechnicianId()))
                .toList();
    }

    private Map<Long, String> supervisores() {
        Map<Long, UserEntity> byId = users.findAll().stream().collect(Collectors.toMap(UserEntity::getId, u -> u));
        Map<Long, String> out = new HashMap<>();
        byId.values().forEach(u -> out.put(u.getId(), u.getSupervisorId() == null || byId.get(u.getSupervisorId()) == null ? "—" : byId.get(u.getSupervisorId()).getFullName()));
        return out;
    }

    /** Nombre completo del técnico o supervisor al que se refiere el texto (todos sus nombres, o un apellido/nombre único). */
    private String personaDe(String s) {
        List<UserEntity> todos = users.findAll().stream().filter(u -> u.getRole() != Role.ADMINISTRADOR).toList();
        for (UserEntity u : todos) {
            String[] t = norm(u.getFullName()).split("\\s+");
            if (t.length >= 2 && Arrays.stream(t).allMatch(s::contains)) {
                return u.getFullName();
            }
        }
        for (UserEntity u : todos) {
            for (String t : norm(u.getFullName()).split("\\s+")) {
                long coincide = todos.stream().filter(x -> norm(x.getFullName()).contains(t)).count();
                if (t.length() >= 4 && coincide == 1 && Pattern.compile("\\b" + t + "\\b").matcher(s).find()) {
                    return u.getFullName();
                }
            }
        }
        return null;
    }

    private static LocalDate[] periodoOpt(String s) {
        LocalDate hoy = LocalDate.now();
        if (s.contains("mes anterior") || s.contains("mes pasado")) {
            LocalDate d = hoy.minusMonths(1).withDayOfMonth(1);
            return new LocalDate[]{d, d.withDayOfMonth(d.lengthOfMonth())};
        }
        if (s.contains("este mes") || s.contains("mes actual")) {
            return new LocalDate[]{hoy.withDayOfMonth(1), hoy};
        }
        for (int i = 0; i < MESES.length; i++) {
            if (s.contains(MESES[i])) {
                LocalDate d = LocalDate.of(hoy.getYear(), i + 1, 1);
                return new LocalDate[]{d, d.withDayOfMonth(d.lengthOfMonth())};
            }
        }
        if (s.contains("ayer")) {
            return new LocalDate[]{hoy.minusDays(1), hoy.minusDays(1)};
        }
        if (s.contains("hoy")) {
            return new LocalDate[]{hoy, hoy};
        }
        if (s.contains("semana")) {
            return new LocalDate[]{hoy.minusDays(6), hoy};
        }
        Matcher n = Pattern.compile("ultimos? (\\d{1,3}) dias").matcher(s);
        if (n.find()) {
            return new LocalDate[]{hoy.minusDays(Integer.parseInt(n.group(1)) - 1L), hoy};
        }
        return null;
    }

    /** Excel (.xlsx) con título, fecha de generación, encabezados con filtro y una fila por minuta; el puntaje va como número. */
    public byte[] reporteXlsx(AuthenticatedUser user, Map<String, Object> consulta) {
        List<Minuta> filas = filtrar(consulta, user, visibles(user));
        Map<Long, String> sup = supervisores();
        List<List<Object>> datos = new ArrayList<>();
        for (Minuta m : filas) {
            datos.add(new ArrayList<>(Arrays.asList(str(m.getClientName()), str(m.getTechnicianName()), sup.getOrDefault(m.getTechnicianId(), "—"),
                    ReportesService.estadoDe(m), m.getPuntaje(), fecha(m), str(m.getAddress()))));
        }
        return XlsxWriter.hoja("Reporte de minutas · " + descripcion(consulta), "Generado el " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                + " · " + filas.size() + " minutas", List.of("Local", "Técnico", "Supervisor", "Estado", "Puntaje", "Fecha", "Dirección"), datos);
    }

    private Map<String, Object> libre(String texto, AuthenticatedUser user, List<Minuta> visibles) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rol", user.isAdmin() ? "adm" : user.isSupervisor() ? "sup" : "tec");
        body.put("texto", texto);
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("reglas", reglas.findAll().stream().filter(ReglaEmpresa::isActiva).map(r -> r.getId() + ": " + r.getNombre() + ". " + r.getTexto()).toList());
        ctx.put("conocimiento", fichas.findAll().stream().map(f -> f.getCodigo() + ": " + f.getAprendizaje()).toList());
        ctx.put("minutas", visibles.stream().limit(15).map(m -> m.getClientName() + " · " + fecha(m) + " · " + etiqueta(m)
                + (puedeVerDetalle(m, user) ? " · " + str(read(m.getContentJson()).get("obsTec")) : "")).toList());
        body.put("contexto", ctx);
        Map<String, Object> r = fastApi.post("/asistente/mensaje", body);
        if ("aprendizaje".equals(r.get("tipo"))) {
            Map<String, Object> a = aprendizaje.hoy(user);
            a.put("bloques", List.of(texto("Revisando lo aprendido hoy.")));
            a.put("aprendizaje", true);
            return a;
        }
        Map<String, Object> b = bloque("chatgpt".equals(r.get("fuente")) ? "texto_ia" : "texto", null, str(r.get("texto")));
        if ("chatgpt".equals(r.get("fuente"))) {
            b.put("badge", "IA");
        }
        Map<String, Object> out = out(b);
        out.put("tipo", r.get("tipo"));
        out.put("texto", r.get("texto"));
        if (r.get("accion") != null) {
            out.put("accion", r.get("accion"));
        }
        return out;
    }

    // ---------------------------------------------------------------- utilidades de datos

    private List<Minuta> visibles(AuthenticatedUser user) {
        return empresa.contadas(minutas.findAllByOrderByCreatedAtDesc());
    }

    /** Lo que cada rol puede sumar: el técnico, lo suyo; el supervisor, su equipo; el administrador, todo. */
    private List<Minuta> alcance(AuthenticatedUser user, List<Minuta> todas) {
        if (user.isAdmin()) {
            return todas;
        }
        if (user.isSupervisor()) {
            Set<Long> equipo = users.findByRole(Role.TECNICO).stream().filter(t -> Objects.equals(t.getSupervisorId(), user.id()))
                    .map(UserEntity::getId).collect(Collectors.toSet());
            return todas.stream().filter(m -> m.getTechnicianId() != null && equipo.contains(m.getTechnicianId())).toList();
        }
        return todas.stream().filter(m -> Objects.equals(m.getTechnicianId(), user.id())).toList();
    }

    private boolean puedeVerDetalle(Minuta m, AuthenticatedUser user) {
        return user.isAdmin() || user.isSupervisor() || Objects.equals(m.getTechnicianId(), user.id());
    }

    private record Match(Minuta minuta, String fechaNoCoincide) {
    }

    private Match buscar(String texto, List<Minuta> lista) {
        String s = norm(texto);
        Matcher dm = Pattern.compile("(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?").matcher(texto);
        Integer dd = null, mm = null;
        if (dm.find()) {
            dd = Integer.parseInt(dm.group(1));
            mm = Integer.parseInt(dm.group(2));
        }
        Minuta best = null;
        double bestScore = 0;
        for (Minuta m : lista) {
            List<String> toks = Arrays.stream(norm(m.getClientName()).replaceAll("\\(.*?\\)", "").split("[^a-z0-9ñ]+"))
                    .filter(w -> w.length() >= 4 && !VACIAS.contains(w)).toList();
            if (toks.isEmpty()) {
                continue;
            }
            double hit = toks.stream().filter(s::contains).count() / (double) toks.size();
            double sc = hit;
            if (dd != null && m.getCreatedAt() != null && m.getCreatedAt().getDayOfMonth() == dd && m.getCreatedAt().getMonthValue() == mm) {
                sc += .5;
            }
            if (hit >= .5 && sc > bestScore) {
                best = m;
                bestScore = sc;
            }
        }
        if (best == null) {
            return null;
        }
        String no = null;
        if (dd != null && best.getCreatedAt() != null && (best.getCreatedAt().getDayOfMonth() != dd || best.getCreatedAt().getMonthValue() != mm)) {
            no = dd + "/" + mm;
        }
        return new Match(best, no);
    }

    /** «Actualiza la minuta de X del 10/10: el router quedó en el rack» → «el router quedó en el rack». */
    private static String cambios(String texto) {
        Matcher m = Pattern.compile("^(.*?\\bminuta\\b.*?)(?::\\s+|\\s+ya que\\s+|\\s+porque\\s+|,?\\s+con lo siguiente:?\\s*|,?\\s+indicando que\\s+)(.+)$",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(texto.trim());
        if (m.find()) {
            String r = m.group(2).trim();
            return r.substring(0, 1).toUpperCase() + r.substring(1);
        }
        return "";
    }

    private List<Map<String, Object>> recientes(List<Minuta> l, int n) {
        List<Map<String, Object>> acc = new ArrayList<>();
        l.stream().limit(n).forEach(m -> acc.add(accion("texto", m.getClientName() + " · " + fecha(m), false,
                Map.of("texto", "Actualiza la minuta de " + m.getClientName() + " del " + fecha(m) + ": "))));
        Map<String, Object> b = bloque("opciones", "Minutas recientes", "O elige una:");
        b.put("acciones", acc);
        return List.of(b);
    }

    private Map<String, Object> item(Minuta m, String detalle) {
        Map<String, Object> i = new LinkedHashMap<>();
        i.put("minutaId", m.getId());
        i.put("titulo", m.getClientName());
        i.put("detalle", detalle);
        return i;
    }

    static String etiqueta(Minuta m) {
        return switch (m.getStatus()) {
            case BORRADOR -> m.getRejectionReason() != null ? "Rechazada" : "Pendiente";
            case COMPLETA -> "Completa";
            case VALIDADA -> "Validada";
        };
    }

    private static String fecha(Minuta m) {
        return m.getCreatedAt() == null ? "—" : m.getCreatedAt().format(DMY);
    }

    // ---------------------------------------------------------------- bloques

    private static Map<String, Object> bloque(String tipo, String titulo, String texto) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("tipo", tipo);
        if (titulo != null) {
            b.put("titulo", titulo);
        }
        if (texto != null) {
            b.put("texto", texto);
        }
        return b;
    }

    private static Map<String, Object> texto(String t) {
        return bloque("texto", null, t);
    }

    private static Map<String, Object> accion(String id, String label, boolean principal, Map<String, Object> datos) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("id", id);
        a.put("label", label);
        a.put("principal", principal);
        a.put("datos", datos);
        return a;
    }

    private static Map<String, Object> accionBloque(String id, Long minutaId) {
        Map<String, Object> b = bloque("accion", null, null);
        b.put("accion", id);
        b.put("minutaId", minutaId);
        return b;
    }

    @SafeVarargs
    private static Map<String, Object> out(Map<String, Object>... bloques) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("bloques", new ArrayList<>(Arrays.asList(bloques)));
        Map<String, Object> primero = bloques.length == 0 ? null : bloques[0];
        r.put("tipo", primero == null ? "texto" : primero.get("tipo"));
        r.put("texto", primero == null ? "" : primero.getOrDefault("texto", primero.getOrDefault("titulo", "")));
        return r;
    }

    private static Map<String, Object> out(Map<String, Object> bloque, List<Map<String, Object>> otros) {
        Map<String, Object> r = out(bloque);
        bloques(r).addAll(otros);
        return r;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> bloques(Map<String, Object> r) {
        return (List<Map<String, Object>>) r.get("bloques");
    }

    private static List<String> fila(String k, Object v) {
        String s = v == null ? "" : String.valueOf(v).trim();
        return List.of(k, s.isEmpty() ? "Sin dato" : s);
    }

    // ---------------------------------------------------------------- varios

    private static String norm(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static Long num(Object o) {
        return o instanceof Number n ? n.longValue() : (o == null || String.valueOf(o).isBlank() ? null : Long.valueOf(String.valueOf(o)));
    }

    private static List<String> strList(Object o) {
        return o instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
    }

    private static int size(Object o) {
        return o instanceof List<?> l ? l.size() : 0;
    }

    private static String firstNonBlank(String... v) {
        for (String s : v) {
            if (s != null && !s.isBlank()) {
                return s;
            }
        }
        return "";
    }

    private static String firstLine(String s) {
        return s == null ? "" : s.split("\n")[0];
    }

    private static String csv(String s) {
        return s == null ? "" : s.replace(";", ",").replace("\n", " ");
    }

    private Map<String, Object> read(String json) {
        try {
            return json == null || json.isBlank() ? new LinkedHashMap<>() : mapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String write(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String resumen(List<Minuta> l) {
        long v = l.stream().filter(m -> m.getStatus() == MinutaStatus.VALIDADA).count();
        long c = l.stream().filter(m -> m.getStatus() == MinutaStatus.COMPLETA).count();
        long b = l.stream().filter(m -> m.getStatus() == MinutaStatus.BORRADOR).count();
        return v + " validadas, " + c + " por validar y " + b + " sin terminar";
    }

    /** Cancela un reporte programado del propio usuario. */
    public Map<String, Object> cancelarProgramado(Long id, AuthenticatedUser user) {
        programados.findById(id).filter(p -> Objects.equals(p.getUserId(), user.id())).ifPresent(programados::delete);
        return out(texto("Listo, ya no te enviaré ese reporte."));
    }

    /** Último día de cada mes a las 7:00: arma cada reporte programado con el mes en curso y lo envía por notificación. */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 7 * * *")
    public void enviarProgramados() {
        LocalDate hoy = LocalDate.now();
        if (hoy.getDayOfMonth() != hoy.lengthOfMonth()) {
            return;
        }
        for (ReporteProgramado p : programados.findAll()) {
            try {
                if (p.getUltimoEnvio() != null && p.getUltimoEnvio().toLocalDate().equals(hoy)) {
                    continue;
                }
                UserEntity u = users.findById(p.getUserId()).orElse(null);
                if (u == null) {
                    continue;
                }
                AuthenticatedUser au = new AuthenticatedUser(u.getId(), u.getEmail(), u.getFullName(), u.getRole());
                Map<String, Object> c = read(p.getConsultaJson());
                c.put("desde", hoy.withDayOfMonth(1).toString());
                c.put("hasta", hoy.toString());
                List<Minuta> filas = filtrar(c, au, visibles(au));
                String cuerpo = "Reporte de fin de mes: " + filas.size() + (filas.size() == 1 ? " minuta" : " minutas") + " del " + hoy.withDayOfMonth(1).format(DMY)
                        + " al " + hoy.format(DMY) + ". " + resumen(filas) + ". Pídele al Asistente IA el detalle o el Excel.";
                notificaciones.save(com.upc.idbi.gateway.notification.UserNotification.builder().userId(u.getId()).title("Reporte de fin de mes").body(cuerpo)
                        .type("REPORTE_PROGRAMADO").createdAt(LocalDateTime.now()).build());
                p.setUltimoEnvio(LocalDateTime.now());
                programados.save(p);
            } catch (RuntimeException ignored) {
                // un reporte que falla no debe frenar a los demás
            }
        }
    }
}
