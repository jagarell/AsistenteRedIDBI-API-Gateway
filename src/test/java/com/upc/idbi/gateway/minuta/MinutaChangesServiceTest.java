package com.upc.idbi.gateway.minuta;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.idbi.gateway.auth.Role;
import com.upc.idbi.gateway.common.FastApiClient;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinutaChangesServiceTest {

    @Test
    void aplicarSubeLaVersionGuardaHistorialYVuelveAValidacion() {
        MinutaService minutas = mock(MinutaService.class);
        MinutaRepository repo = mock(MinutaRepository.class);
        MinutaVersionRepository versions = mock(MinutaVersionRepository.class);
        Minuta m = Minuta.builder().id(5L).clientName("Muna").status(MinutaStatus.VALIDADA).validatedByName("Sup").createdAt(LocalDateTime.now()).build();
        when(minutas.get(5L)).thenReturn(m);
        when(repo.save(any(Minuta.class))).thenAnswer(inv -> inv.getArgument(0));
        MinutaChangesService s = new MinutaChangesService(minutas, repo, versions, mock(FastApiClient.class), new ObjectMapper());

        Minuta out = s.aplicar(5L, List.of(
                Map.of("k", "cred", "t", "Red WiFi · contraseña abc", "v", Map.of("eq", "Red WiFi", "usr", "MOVISTAR_F653", "pw", "abc")),
                Map.of("k", "pasoC", "t", "Avisar si vuelve a fallar.")), new AuthenticatedUser(1L, "t@x.pe", "Técnico", Role.TECNICO));

        assertThat(out.getVersion()).isEqualTo(2);
        assertThat(out.getStatus()).isEqualTo(MinutaStatus.BORRADOR);
        assertThat(out.getValidatedByName()).isNull();
        assertThat(out.getAccesosJson()).contains("MOVISTAR_F653").contains("abc");
        assertThat(out.getContentJson()).contains("Avisar si vuelve a fallar.");
        verify(versions).save(any(MinutaVersion.class));
    }
}
