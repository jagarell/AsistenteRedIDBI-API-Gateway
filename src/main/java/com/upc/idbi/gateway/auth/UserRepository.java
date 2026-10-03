package com.upc.idbi.gateway.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<UserEntity> findByEmailIgnoreCase(String email);

    /** Usuarios de un rol con un dispositivo registrado para push. */
    List<UserEntity> findByRoleAndFcmTokenIsNotNull(Role role);
}