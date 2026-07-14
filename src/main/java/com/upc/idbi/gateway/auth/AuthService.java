package com.upc.idbi.gateway.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        String normalizedEmail = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (!request.password().equals(request.confirmPassword())) {
            throw new IllegalArgumentException(
                    "Las contraseñas no coinciden"
            );
        }

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException(
                    "Ya existe una cuenta registrada con ese correo"
            );
        }

        UserEntity user = UserEntity.builder()
                .fullName(request.fullName().trim())
                .email(normalizedEmail)
                .phone(request.phone().trim())
                .company(request.company().trim())
                .city(
                        request.city() == null || request.city().isBlank()
                                ? "Lima"
                                : request.city().trim()
                )
                .password(passwordEncoder.encode(request.password()))
                .role("TECNICO")
                .createdAt(LocalDateTime.now())
                .build();

        UserEntity savedUser = userRepository.save(user);

        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getFullName(),
                savedUser.getEmail(),
                savedUser.getPhone(),
                savedUser.getCompany(),
                savedUser.getCity(),
                savedUser.getRole(),
                "Usuario registrado correctamente"
        );
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {

        String normalizedEmail = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        UserEntity user = userRepository
                .findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Correo o contraseña incorrectos"
                ));

        boolean passwordMatches = passwordEncoder.matches(
                request.password(),
                user.getPassword()
        );

        if (!passwordMatches) {
            throw new IllegalArgumentException(
                    "Correo o contraseña incorrectos"
            );
        }

        String accessToken = UUID.randomUUID().toString();
        int expiresInMinutes = 60;

        return new LoginResponse(
                accessToken,
                expiresInMinutes,
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole()
        );
    }
}