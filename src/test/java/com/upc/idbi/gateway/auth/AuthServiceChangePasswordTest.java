package com.upc.idbi.gateway.auth;

import com.upc.idbi.gateway.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceChangePasswordTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock RecoveryCodeNotifier recoveryCodeNotifier;

    @InjectMocks
    AuthService service;

    private UserEntity usuario() {
        UserEntity u = new UserEntity();
        u.setId(7L);
        u.setPassword("hash-actual");
        return u;
    }

    @Test
    void cambiaLaContrasenaSiLaActualEsCorrecta() {
        UserEntity u = usuario();
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("vieja1234", "hash-actual")).thenReturn(true);
        when(passwordEncoder.matches("nueva1234", "hash-actual")).thenReturn(false);
        when(passwordEncoder.encode("nueva1234")).thenReturn("hash-nuevo");

        ResetPasswordResponse r = service.changePassword(7L, new ChangePasswordRequest("vieja1234", "nueva1234"));

        assertThat(r.message()).contains("actualizada");
        assertThat(u.getPassword()).isEqualTo("hash-nuevo");
        verify(userRepository).save(u);
    }

    @Test
    void rechazaSiLaActualEsIncorrecta() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(usuario()));
        when(passwordEncoder.matches("mala", "hash-actual")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(7L, new ChangePasswordRequest("mala", "nueva1234")))
                .hasMessageContaining("actual es incorrecta");
    }
}
