package com.upc.idbi.gateway.roles;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionRepository extends JpaRepository<RolePermission, String> {
}
