package com.upc.idbi.gateway.equipo;

import com.upc.idbi.gateway.auth.Role;
import com.upc.idbi.gateway.auth.UserEntity;
import com.upc.idbi.gateway.auth.UserRepository;
import com.upc.idbi.gateway.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Agregar persona (panel web): solo el administrador, con correo único, contraseña mínima y el tope de usuarios del plan. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgregarPersonaTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;

    private UserEntity usuario(String email, Role rol) {
        return users.save(UserEntity.builder().fullName("Persona " + email).email(email).phone("999999999").company("ACME").city("Lima")
                .password("x").role(rol).createdAt(LocalDateTime.now()).build());
    }

    private String token(UserEntity u) {
        return "Bearer " + jwt.generateToken(u);
    }

    private String json(String nombre, String email, String rol, String pass) {
        return "{\"nombre\":\"" + nombre + "\",\"email\":\"" + email + "\",\"rol\":\"" + rol + "\",\"password\":\"" + pass + "\"}";
    }

    @Test
    void elAdministradorAgregaUnaPersonaConSuRol() throws Exception {
        users.deleteAll();
        UserEntity admin = usuario("admin-t@test.local", Role.ADMINISTRADOR);
        mvc.perform(post("/api/equipo").header("Authorization", token(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Ana Sup", "ana@test.local", "SUPERVISOR", "ClaveInicial1")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.rol").value("SUPERVISOR")).andExpect(jsonPath("$.email").value("ana@test.local"));
        UserEntity ana = users.findByEmailIgnoreCase("ana@test.local").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("ACME", ana.getCompany());
        org.junit.jupiter.api.Assertions.assertNotEquals("ClaveInicial1", ana.getPassword());
    }

    @Test
    void soloElAdministradorPuedeAgregar() throws Exception {
        users.deleteAll();
        UserEntity sup = usuario("sup-t@test.local", Role.SUPERVISOR);
        mvc.perform(post("/api/equipo").header("Authorization", token(sup)).contentType(MediaType.APPLICATION_JSON)
                .content(json("Otro", "otro@test.local", "TECNICO", "ClaveInicial1"))).andExpect(status().isForbidden());
    }

    @Test
    void validaCorreoClaveRolYDuplicados() throws Exception {
        users.deleteAll();
        UserEntity admin = usuario("admin-t@test.local", Role.ADMINISTRADOR);
        String t = token(admin);
        mvc.perform(post("/api/equipo").header("Authorization", t).contentType(MediaType.APPLICATION_JSON).content(json("X", "no-es-correo", "TECNICO", "ClaveInicial1"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/equipo").header("Authorization", t).contentType(MediaType.APPLICATION_JSON).content(json("X", "x@test.local", "TECNICO", "corta"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/equipo").header("Authorization", t).contentType(MediaType.APPLICATION_JSON).content(json("X", "x@test.local", "ADMINISTRADOR", "ClaveInicial1"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/equipo").header("Authorization", t).contentType(MediaType.APPLICATION_JSON).content(json("Dup", "admin-t@test.local", "TECNICO", "ClaveInicial1"))).andExpect(status().isConflict());
    }

    @Test
    void respetaElTopeDeUsuariosDelPlan() throws Exception {
        users.deleteAll();
        UserEntity admin = usuario("admin-t@test.local", Role.ADMINISTRADOR);
        for (int i = 1; i < EquipoController.TOPE_USUARIOS; i++) usuario("u" + i + "@test.local", Role.TECNICO);
        mvc.perform(post("/api/equipo").header("Authorization", token(admin)).contentType(MediaType.APPLICATION_JSON)
                .content(json("De más", "extra@test.local", "TECNICO", "ClaveInicial1"))).andExpect(status().isConflict());
    }
}
