package com.upc.idbi.gateway.bitacora;

import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BitacoraService {

    private final BitacoraRepository repo;

    /** Registra un evento. Un evento igual (misma minuta, usuario y acción) dentro de 3 segundos se descarta. Nunca interrumpe la acción original. */
    public void registrar(AuthenticatedUser user, Long minutaId, String tipo, String accion) {
        try {
            LocalDateTime ahora = LocalDateTime.now();
            Long uid = user == null ? null : user.id();
            if (uid != null && repo.existsByMinutaIdAndUsuarioIdAndAccionAndTsAfter(minutaId, uid, accion, ahora.minusSeconds(3))) {
                return;
            }
            repo.save(Bitacora.builder().minutaId(minutaId).usuarioId(uid).usuarioNombre(user == null ? "Sistema" : user.fullName())
                    .rol(user == null ? "SISTEMA" : user.role().name()).tipo(tipo).accion(accion).ts(ahora).build());
        } catch (RuntimeException ignored) {
            // la trazabilidad no debe romper una validación o una edición
        }
    }
}
