package com.upc.idbi.gateway.roles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    RolePermissionRepository repository;

    @InjectMocks
    RoleService service;

    private Map<String, RolePermission> byId() {
        return RoleService.defaults().stream().collect(Collectors.toMap(RolePermission::getId, r -> r));
    }

    @Test
    void pestanasDeReportesPorRolBase() {
        Map<String, RolePermission> r = byId();
        assertThat(RoleService.profile(r.get("admin"))).isEqualTo(new RoleService.ReportProfile(true, "all", true));
        assertThat(RoleService.profile(r.get("sup"))).isEqualTo(new RoleService.ReportProfile(false, "mine", true));
        assertThat(RoleService.profile(r.get("tec"))).isEqualTo(new RoleService.ReportProfile(false, "none", true));
    }

    @Test
    void porTecnicoYMisTecnicosSonExcluyentesYLoBloqueadoSeQueda() {
        Map<String, RolePermission> r = byId();
        when(repository.count()).thenReturn(4L);
        when(repository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(r.get(inv.getArgument(0, String.class))));
        when(repository.save(any(RolePermission.class))).thenAnswer(inv -> inv.getArgument(0));

        RolePermission sup = service.update("sup", null, Set.of("m_all", "r_tec_all", "r_tec_mine", "a_create"));
        assertThat(sup.permSet()).contains("m_all", "a_validate", "a_reject", "n_own", "mod_notif");
        assertThat(sup.permSet().contains("r_tec_all") && sup.permSet().contains("r_tec_mine")).isFalse();
        assertThat(RoleService.profile(sup).all()).isTrue();
    }
}
