package com.upc.idbi.gateway.equipo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.idbi.gateway.auth.Role;
import com.upc.idbi.gateway.auth.UserEntity;
import com.upc.idbi.gateway.auth.UserRepository;
import com.upc.idbi.gateway.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Quitar y devolver el acceso (Equipo y accesos): sin sesión nueva ni renovada, sin borrar nada, y nunca el propio. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QuitarAccesoTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper om;

    private UserEntity usuario(String email, Role rol) {
        return users.save(UserEntity.builder().fullName("Persona " + email).email(email).phone("999999999").company("ACME").city("Lima")
                .password(encoder.encode("Clave12345")).role(rol).createdAt(LocalDateTime.now()).build());
    }

    private String token(UserEntity u) {
        return "Bearer " + jwt.generateToken(u);
    }

    private String loginBody(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"Clave12345\"}";
    }

    private void acceso(UserEntity admin, UserEntity u, boolean con) throws Exception {
        mvc.perform(put("/api/equipo/" + u.getId() + "/acceso").header("Authorization", token(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"conAcceso\":" + con + "}")).andExpect(status().isOk()).andExpect(jsonPath("$.conAcceso").value(con));
    }

    @Test
    void sinAccesoNoPuedeIniciarNiRenovarSesionYAlDevolverloVuelve() throws Exception {
        users.deleteAll();
        UserEntity admin = usuario("admin-q@test.local", Role.ADMINISTRADOR);
        UserEntity tec = usuario("tec-q@test.local", Role.TECNICO);
        // entra y obtiene su refresh token
        String r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody(tec.getEmail())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode j = om.readTree(r);
        String refresh = j.has("refreshToken") ? j.get("refreshToken").asText() : j.path("data").path("refreshToken").asText();
        assertTrue(!refresh.isBlank());

        acceso(admin, tec, false);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody(tec.getEmail())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Tu acceso fue retirado. Habla con el administrador de tu empresa."));
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
        assertTrue(users.findByEmailIgnoreCase(tec.getEmail()).isPresent());   // no se borra a la persona

        acceso(admin, tec, true);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody(tec.getEmail()))).andExpect(status().isOk());
    }

    @Test
    void noPuedesQuitarteTuPropioAccesoNiLoHaceQuienNoEsAdministrador() throws Exception {
        users.deleteAll();
        UserEntity admin = usuario("admin-q@test.local", Role.ADMINISTRADOR);
        UserEntity sup = usuario("sup-q@test.local", Role.SUPERVISOR);
        mvc.perform(put("/api/equipo/" + admin.getId() + "/acceso").header("Authorization", token(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"conAcceso\":false}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/equipo/" + admin.getId() + "/acceso").header("Authorization", token(sup)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"conAcceso\":false}")).andExpect(status().isForbidden());
    }

    @Test
    void laListaIndicaQuienTieneAcceso() throws Exception {
        users.deleteAll();
        UserEntity admin = usuario("admin-q@test.local", Role.ADMINISTRADOR);
        UserEntity tec = usuario("tec-q@test.local", Role.TECNICO);
        acceso(admin, tec, false);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/equipo").header("Authorization", token(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='tec-q@test.local')].conAcceso").value(false))
                .andExpect(jsonPath("$[?(@.email=='admin-q@test.local')].conAcceso").value(true));
    }
}
