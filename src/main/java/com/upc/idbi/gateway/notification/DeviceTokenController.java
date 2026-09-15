package com.upc.idbi.gateway.notification;

import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications/device-token")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    @PutMapping
    public void register(
            @RequestBody DeviceTokenRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        deviceTokenService.updateToken(user.id(), request.token());
    }
}
