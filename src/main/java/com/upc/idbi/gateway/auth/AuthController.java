package com.upc.idbi.gateway.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request
    ) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(
            @Valid @RequestBody RefreshRequest request
    ) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @Valid @RequestBody RefreshRequest request
    ) {
        authService.logout(request);
    }

    @PostMapping("/forgot-password")
    public ForgotPasswordResponse forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        return authService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public ResetPasswordResponse resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        return authService.resetPassword(request);
    }

    /** Cambiar contraseña con la sesión iniciada (pide la actual). */
    @PostMapping("/change-password")
    public ResetPasswordResponse changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal AuthenticatedUser me
    ) {
        if (me == null) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión");
        return authService.changePassword(me.id(), request);
    }
}
