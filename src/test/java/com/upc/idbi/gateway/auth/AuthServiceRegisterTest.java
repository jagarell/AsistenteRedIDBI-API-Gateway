package com.upc.idbi.gateway.auth;

import com.upc.idbi.gateway.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceRegisterTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock RecoveryCodeNotifier recoveryCodeNotifier;

    @InjectMocks
    AuthService service;

    @Test
    void registrarseSiempreCreaUnTecnicoAunqueSePidaSupervisor() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        RegisterResponse response = service.register(new RegisterRequest(
                "Ana Pérez", "ana@idbi.pe", "999999999", "IDBI", "Lima", "SUPERVISOR",
                "clave1234", "clave1234"));

        assertThat(response.role()).isEqualTo("TECNICO");
    }
}
