package com.upc.idbi.gateway.notification;

import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bandeja de notificaciones del usuario: lista, no leídas y marcar como leídas. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class UserNotificationController {

    private final UserNotificationRepository repository;

    @GetMapping
    public Map<String, Object> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        List<Map<String, Object>> items = repository.findTop50ByUserIdOrderByCreatedAtDesc(user.id()).stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("title", n.getTitle());
            m.put("body", n.getBody());
            m.put("type", n.getType());
            m.put("minutaId", n.getMinutaId());
            m.put("evaluationId", n.getEvaluationId());
            m.put("createdAt", n.getCreatedAt() == null ? null : n.getCreatedAt().toString());
            m.put("read", n.getReadAt() != null);
            return m;
        }).toList();
        return Map.of("items", items, "unread", repository.countByUserIdAndReadAtIsNull(user.id()));
    }

    @PostMapping("/read-all")
    @Transactional
    public Map<String, Object> readAll(@AuthenticationPrincipal AuthenticatedUser user) {
        repository.markAllRead(user.id(), LocalDateTime.now());
        return Map.of("unread", 0);
    }
}
