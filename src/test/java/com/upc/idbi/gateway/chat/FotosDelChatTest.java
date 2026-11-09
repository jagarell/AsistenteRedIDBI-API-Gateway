package com.upc.idbi.gateway.chat;

import com.upc.idbi.gateway.auth.Role;
import com.upc.idbi.gateway.auth.UserEntity;
import com.upc.idbi.gateway.auth.UserRepository;
import com.upc.idbi.gateway.evaluation.Evaluation;
import com.upc.idbi.gateway.evaluation.EvaluationRepository;
import com.upc.idbi.gateway.evidence.Evidence;
import com.upc.idbi.gateway.evidence.EvidenceRepository;
import com.upc.idbi.gateway.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Lista de fotos del chat para colocarlas en el mapa (panel web): una por código y alcance, sin documentos del cliente. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FotosDelChatTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired EvaluationRepository evaluations;
    @Autowired EvidenceRepository evidences;

    private String token() {
        UserEntity u = users.save(UserEntity.builder().fullName("Tec").email("tec-fotos@test.local").phone("999999999").company("ACME").city("Lima")
                .password("x").role(Role.TECNICO).createdAt(LocalDateTime.now()).build());
        return "Bearer " + jwt.generateToken(u);
    }

    private void foto(Long ev, String code, String scope, String equipo, String archivo, int minuto) {
        evidences.save(Evidence.builder().evaluationId(ev).category("pos").storedFileName(archivo).uploadedAt(LocalDateTime.now().plusMinutes(minuto))
                .evidenceCode(code).chatScope(scope).chatEquipo(equipo).build());
    }

    @Test
    void listaUnaFotoPorCodigoYAlcanceSinLosDocumentosDelCliente() throws Exception {
        Evaluation e = evaluations.save(Evaluation.builder().restaurantName("Local").createdAt(LocalDateTime.now()).build());
        foto(e.getId(), "E1", "", null, "vieja.jpg", 1);
        foto(e.getId(), "E1", "", null, "nueva.jpg", 2);
        foto(e.getId(), "E5", "L_CAJAS:1", "Caja 1", "caja.jpg", 3);
        foto(e.getId(), "DOC_CLIENTE", "", null, "ruc.jpg", 4);
        mvc.perform(get("/api/evaluations/" + e.getId() + "/chat/evidences").header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("E1"))
                .andExpect(jsonPath("$[0].url").value("/uploads/evidence/" + e.getId() + "/nueva.jpg"))
                .andExpect(jsonPath("$[1].label").value("Caja 1"))
                .andExpect(jsonPath("$[1].scope").value("L_CAJAS:1"));
    }

    @Test
    void sinSesionNoSeListan() throws Exception {
        mvc.perform(get("/api/evaluations/1/chat/evidences")).andExpect(status().isUnauthorized());
    }
}
