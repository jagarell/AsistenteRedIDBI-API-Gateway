package com.upc.idbi.gateway.notification;

import com.upc.idbi.gateway.auth.UserEntity;
import com.upc.idbi.gateway.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final UserRepository userRepository;

    public void updateToken(Long userId, String token) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el usuario con id " + userId
                ));
        user.setFcmToken(token);
        userRepository.save(user);
    }
}
