package com.upc.idbi.gateway.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Locale;

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
                        request.city() == null ||
                                request.city().isBlank()
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
}