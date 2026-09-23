package com.upc.idbi.gateway.auth;

import com.upc.idbi.gateway.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RecoveryCodeNotifier recoveryCodeNotifier;

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
                .role(Role.fromString(request.role()))
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
                savedUser.getRole().name(),
                "Usuario registrado correctamente"
        );
    }

    @Transactional
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

        String accessToken = jwtService.generateToken(user);
        int expiresInMinutes = (int) jwtService.getExpirationMinutes();
        String refreshToken = issueRefreshToken(user);

        return new LoginResponse(
                accessToken,
                refreshToken,
                expiresInMinutes,
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name()
        );
    }

    /**
     * Cambia el access token expirado por uno nuevo usando el refresh token —
     * evita que el técnico tenga que volver a iniciar sesión cada 60 minutos.
     * El refresh token se rota (se borra el usado y se emite uno nuevo) para
     * que uno filtrado/robado deje de servir en cuanto el dueño real lo use.
     */
    @Transactional
    public LoginResponse refresh(RefreshRequest request) {
        String tokenHash = jwtService.hashToken(request.refreshToken());

        RefreshTokenEntity stored = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidRefreshTokenException(
                        "Refresh token inválido. Vuelve a iniciar sesión."
                ));

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(stored);
            throw new InvalidRefreshTokenException(
                    "Tu sesión expiró. Vuelve a iniciar sesión."
            );
        }

        UserEntity user = stored.getUser();
        refreshTokenRepository.delete(stored);

        String accessToken = jwtService.generateToken(user);
        int expiresInMinutes = (int) jwtService.getExpirationMinutes();
        String newRefreshToken = issueRefreshToken(user);

        return new LoginResponse(
                accessToken,
                newRefreshToken,
                expiresInMinutes,
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name()
        );
    }

    /** Revoca el refresh token en el servidor al cerrar sesión manualmente —
     *  idempotente: si ya no existe (expiró, ya se usó, o nunca existió), no
     *  falla, porque el resultado que le importa al cliente (dejar de servir)
     *  ya se cumple. */
    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokenRepository.deleteByTokenHash(
                jwtService.hashToken(request.refreshToken())
        );
    }

    private String issueRefreshToken(UserEntity user) {
        String refreshToken = jwtService.generateRefreshToken();

        refreshTokenRepository.save(
                RefreshTokenEntity.builder()
                        .tokenHash(jwtService.hashToken(refreshToken))
                        .user(user)
                        .createdAt(Instant.now())
                        .expiresAt(Instant.now().plus(
                                jwtService.getRefreshExpirationDays(), ChronoUnit.DAYS
                        ))
                        .build()
        );

        return refreshToken;
    }

    @Transactional
    public ForgotPasswordResponse forgotPassword(
            ForgotPasswordRequest request
    ) {
        String normalizedEmail = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        int expirationMinutes = 10;

        // No revelamos si el correo existe (evita enumeración de usuarios):
        // siempre respondemos igual y sólo enviamos el código si hay cuenta.
        var maybeUser = userRepository.findByEmailIgnoreCase(normalizedEmail);
        if (maybeUser.isEmpty()) {
            return new ForgotPasswordResponse(
                    "Si el correo existe, enviaremos un código de recuperación",
                    expirationMinutes
            );
        }
        UserEntity user = maybeUser.get();

        String recoveryCode = generateRecoveryCode();

        user.setResetCode(recoveryCode);
        user.setResetCodeExpiresAt(
                LocalDateTime.now().plusMinutes(expirationMinutes)
        );

        userRepository.save(user);

        // El código NUNCA se devuelve en la respuesta HTTP ni se registra en
        // logs (política de organización: no exponer códigos de un solo uso).
        // Se entrega por un canal seguro (correo corporativo) vía el notifier.
        recoveryCodeNotifier.send(user.getEmail(), recoveryCode, expirationMinutes);

        return new ForgotPasswordResponse(
                "Si el correo existe, enviaremos un código de recuperación",
                expirationMinutes
        );
    }

    @Transactional
    public ResetPasswordResponse resetPassword(
            ResetPasswordRequest request
    ) {
        String normalizedEmail = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new IllegalArgumentException(
                    "Las contraseñas no coinciden"
            );
        }

        UserEntity user = userRepository
                .findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Correo o código incorrectos"
                ));

        if (user.getResetCode() == null ||
                !user.getResetCode().equals(request.code().trim())) {
            throw new IllegalArgumentException(
                    "El código de recuperación es incorrecto"
            );
        }

        if (user.getResetCodeExpiresAt() == null ||
                user.getResetCodeExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "El código de recuperación ha expirado"
            );
        }

        user.setPassword(
                passwordEncoder.encode(request.newPassword())
        );

        user.setResetCode(null);
        user.setResetCodeExpiresAt(null);

        userRepository.save(user);

        return new ResetPasswordResponse(
                "Contraseña actualizada correctamente"
        );
    }

    private String generateRecoveryCode() {
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }
}