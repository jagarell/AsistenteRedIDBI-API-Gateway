package com.upc.idbi.gateway.profile;

import com.upc.idbi.gateway.auth.UserEntity;
import com.upc.idbi.gateway.auth.UserRepository;
import com.upc.idbi.gateway.evaluation.EvaluationRepository;
import com.upc.idbi.gateway.minuta.Minuta;
import com.upc.idbi.gateway.minuta.MinutaRepository;
import com.upc.idbi.gateway.minuta.MinutaStatus;
import com.upc.idbi.gateway.profile.dto.ProfileResponse;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserRepository userRepository;
    private final EvaluationRepository evaluationRepository;
    private final MinutaRepository minutaRepository;

    /** Perfil real del usuario autenticado + estadísticas para el Home
     * ("Evaluaciones" = total del sistema; "Propuestas"/"Enviadas" = minutas
     * de este técnico, enviadas = todas las que ya no están en borrador). */
    @GetMapping("/me")
    public ProfileResponse getProfile(@AuthenticationPrincipal AuthenticatedUser principal) {
        UserEntity user = userRepository.findById(principal.id())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el usuario con id " + principal.id()
                ));

        List<Minuta> myMinutas = minutaRepository.findByTechnicianIdOrderByCreatedAtDesc(user.getId());
        long sent = myMinutas.stream()
                .filter(m -> m.getStatus() != MinutaStatus.BORRADOR)
                .count();

        return new ProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getCompany(),
                user.getCity(),
                user.getRole().name(),
                (int) evaluationRepository.count(),
                myMinutas.size(),
                (int) sent
        );
    }
}
