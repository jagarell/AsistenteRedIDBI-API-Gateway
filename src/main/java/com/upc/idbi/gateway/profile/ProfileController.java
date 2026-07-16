package com.upc.idbi.gateway.profile;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    @GetMapping
    public Map<String, Object> getProfile() {
        return Map.of(
                "fullName", "Carlos Rodríguez",
                "company", "TechNet Solutions",
                "role", "Técnico Senior",
                "evaluations", 24,
                "proposals", 18,
                "sent", 12
        );
    }
}