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

    /** Avisa a todos los supervisores con un dispositivo registrado. */
    public void minutaCompleted(Minuta minuta) {
        String technician = minuta.getTechnicianName() == null ? "Un técnico" : minuta.getTechnicianName();
        String body = technician + " completó la minuta de " + minuta.getClientName() + ". Está lista para validar.";
        Map<String, String> data = Map.of("type", "MINUTA_COMPLETADA", "minutaId", String.valueOf(minuta.getId()));
        for (UserEntity supervisor : users.findByRoleAndFcmTokenIsNotNull(Role.SUPERVISOR)) {
            push.trySend(supervisor.getFcmToken(), "Minuta por validar", body, data);
        }
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
                    return push.trySend(technician.getFcmToken(), "Minuta por vencer", body,
                            Map.of("type", "MINUTA_POR_VENCER", "minutaId", String.valueOf(minuta.getId())));
                })
                .orElse(false);
    }
}
