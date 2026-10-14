package com.upc.idbi.gateway.notification;

import com.upc.idbi.gateway.auth.Role;
import com.upc.idbi.gateway.auth.UserEntity;
import com.upc.idbi.gateway.auth.UserRepository;
import com.upc.idbi.gateway.minuta.Minuta;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Avisos push de las minutas:
 * <ul>
 *     <li>A los supervisores, cuando un técnico marca una minuta como completa.</li>
 *     <li>Al técnico, cuando su borrador está por vencer.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class MinutaNotifier {

    private final PushNotificationService push;
    private final UserRepository users;
    private final UserNotificationRepository inbox;

    /** Avisa a todos los supervisores con un dispositivo registrado. */
    public void minutaCompleted(Minuta minuta) {
        String technician = minuta.getTechnicianName() == null ? "Un técnico" : minuta.getTechnicianName();
        String body = technician + " completó la minuta de " + minuta.getClientName() + ". Está lista para validar.";
        Map<String, String> data = data("MINUTA_COMPLETA", minuta);
        // Todos los supervisores ven el aviso en su bandeja; el push sale solo a los que tienen dispositivo registrado.
        for (UserEntity supervisor : users.findByRole(Role.SUPERVISOR)) {
            save(supervisor.getId(), "Minuta por validar", body, minuta, "MINUTA_COMPLETA");
        }
        for (UserEntity supervisor : users.findByRoleAndFcmTokenIsNotNull(Role.SUPERVISOR)) {
            push.trySend(supervisor.getFcmToken(), "Minuta por validar", body, data);
        }
    }

    /** Avisa al técnico que hizo la minuta que el supervisor la validó (el PDF ya sale sin la marca SIN VALIDAR). */
    public void minutaValidated(Minuta minuta) {
        if (minuta.getTechnicianId() == null) {
            return;
        }
        users.findById(minuta.getTechnicianId()).ifPresent(technician -> {
            String supervisor = minuta.getValidatedByName() == null ? "El supervisor" : minuta.getValidatedByName();
            String body = supervisor + " validó la minuta de " + minuta.getClientName()
                    + ". El PDF ya sale sin la marca de agua SIN VALIDAR.";
            save(technician.getId(), "Minuta validada", body, minuta, "MINUTA_VALIDADA");
            push.trySend(technician.getFcmToken(), "Minuta validada", body, data("MINUTA_VALIDADA", minuta));
        });
    }

    /** Avisa al técnico que el supervisor devolvió su minuta: debe corregirla y volver a completarla. */
    public void minutaRejected(Minuta minuta) {
        if (minuta.getTechnicianId() == null) {
            return;
        }
        users.findById(minuta.getTechnicianId()).ifPresent(technician -> {
            String supervisor = minuta.getRejectedByName() == null ? "El supervisor" : minuta.getRejectedByName();
            String body = supervisor + " rechazó la minuta de " + minuta.getClientName() + ": "
                    + minuta.getRejectionReason() + ". Corrígela y vuelve a completarla.";
            save(technician.getId(), "Minuta rechazada", body, minuta, "MINUTA_RECHAZADA");
            push.trySend(technician.getFcmToken(), "Minuta rechazada", body, data("MINUTA_RECHAZADA", minuta));
        });
    }

    /** Guarda el aviso en la bandeja del usuario; nunca debe impedir completar o validar la minuta. */
    private void save(Long userId, String title, String body, Minuta minuta, String type) {
        if (userId == null) {
            return;
        }
        try {
            inbox.save(UserNotification.builder().userId(userId).title(title).body(body).type(type)
                    .minutaId(minuta.getId()).evaluationId(minuta.getEvaluationId())
                    .createdAt(java.time.LocalDateTime.now()).build());
        } catch (RuntimeException e) {
            // el aviso en la bandeja es un extra
        }
    }

    /** `tipo`, `minutaId` y `evaluationId` permiten abrir esa minuta al tocar la notificación. */
    private static Map<String, String> data(String tipo, Minuta minuta) {
        Map<String, String> data = new java.util.HashMap<>();
        data.put("tipo", tipo);
        data.put("type", tipo);
        data.put("minutaId", String.valueOf(minuta.getId()));
        if (minuta.getEvaluationId() != null) {
            data.put("evaluationId", String.valueOf(minuta.getEvaluationId()));
        }
        return data;
    }

    /**
     * Avisa al técnico que creó el borrador.
     *
     * @return true si el aviso salió (para no repetirlo mañana)
     */
    public boolean draftExpiring(Minuta minuta, long daysLeft) {
        if (minuta.getTechnicianId() == null) {
            return false;
        }
        return users.findById(minuta.getTechnicianId())
                .map(technician -> {
                    String when = daysLeft <= 0 ? "vence hoy" : daysLeft == 1 ? "vence mañana" : "vence en " + daysLeft + " días";
                    String body = "Tu minuta de " + minuta.getClientName() + " está en borrador y " + when + ". Complétala a tiempo.";
                    save(technician.getId(), "Minuta por vencer", body, minuta, "MINUTA_POR_VENCER");
                    return push.trySend(technician.getFcmToken(), "Minuta por vencer", body,
                            data("MINUTA_POR_VENCER", minuta));
                })
                .orElse(false);
    }
}
