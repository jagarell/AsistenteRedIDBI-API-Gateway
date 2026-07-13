package com.upc.idbi.gateway.auth;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        return Map.of(
                "accessToken", "demo-jwt-token",
                "user", Map.of(
                        "id", 1,
                        "fullName", "Carlos Rodríguez",
                        "email", body.getOrDefault("email", "tecnico@technet.com"),
                        "company", "TechNet Solutions",
                        "role", "Técnico Senior"
                )
        );
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body) {
        return Map.of(
                "accessToken", "demo-jwt-token",
                "message", "Usuario registrado correctamente"
        );
    }
}