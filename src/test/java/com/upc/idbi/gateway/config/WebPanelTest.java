package com.upc.idbi.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El panel web se entrega sin sesión (la pantalla de inicio de sesión es parte de él); la API sigue protegida. */
@SpringBootTest
@AutoConfigureMockMvc
class WebPanelTest {

    @Autowired
    MockMvc mvc;

    @Test
    void laPaginaDelPanelEsPublica() throws Exception {
        mvc.perform(get("/web/")).andExpect(status().isOk());
        mvc.perform(get("/web/index.html")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Network Assistant")));
        mvc.perform(get("/web/app.js")).andExpect(status().isOk());
        mvc.perform(get("/web/app.css")).andExpect(status().isOk());
    }

    @Test
    void laApiSigueRequiriendoSesion() throws Exception {
        mvc.perform(get("/api/panel")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/inicio/indicadores")).andExpect(status().isUnauthorized());
    }
}
