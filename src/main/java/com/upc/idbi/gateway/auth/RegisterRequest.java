package com.upc.idbi.gateway.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "El nombre es obligatorio")
        String fullName,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no es válido")
        String email,

        @NotBlank(message = "El teléfono es obligatorio")
        String phone,

        @NotBlank(message = "La empresa es obligatoria")
        String company,

        String city,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String password,

        @NotBlank(message = "Debes confirmar la contraseña")
        String confirmPassword
) {
}