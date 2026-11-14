package com.upc.idbi.gateway.voz;

import com.upc.idbi.gateway.auth.Role;
import com.upc.idbi.gateway.auth.UserEntity;
import com.upc.idbi.gateway.auth.UserRepository;
import com.upc.idbi.gateway.security.JwtService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Dictado por voz: el gateway reenvía el audio a la API y devuelve el texto o un mensaje claro. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TranscribirControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @MockBean RestTemplate restTemplate;

    private String token() {
        UserEntity u = users.save(UserEntity.builder().fullName("Tec").email("tec-voz@test.local").phone("999999998").company("ACME").city("Lima")
                .password("x").role(Role.TECNICO).createdAt(LocalDateTime.now()).build());
        return "Bearer " + jwt.generateToken(u);
    }

    @Test
    void sinSesionNoSeTranscribe() throws Exception {
        mvc.perform(multipart("/api/transcribir").file(new MockMultipartFile("audio", "a.m4a", "audio/mp4", new byte[2000])))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void reenviaElAudioALaApiYDevuelveElTexto() throws Exception {
        when(restTemplate.postForObject(anyString(), org.mockito.ArgumentMatchers.any(), eq(Map.class)))
                .thenReturn(Map.of("text", "La caja uno tiene AnyDesk", "error", ""));
        mvc.perform(multipart("/api/transcribir").file(new MockMultipartFile("audio", "a.m4a", "audio/mp4", new byte[2000]))
                        .header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("La caja uno tiene AnyDesk"));
        ArgumentCaptor<Object> cuerpo = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).postForObject(url.capture(), cuerpo.capture(), eq(Map.class));
        assertTrue(url.getValue().endsWith("/transcribir"));
        Map<String, Object> m = (Map<String, Object>) cuerpo.getValue();
        assertEquals("audio/mp4", m.get("mime"));
        assertTrue(String.valueOf(m.get("audioBase64")).length() > 100);
    }

    @Test
    void siLaApiFallaDaUnMensajeClaroSinError500() throws Exception {
        when(restTemplate.postForObject(anyString(), org.mockito.ArgumentMatchers.any(), eq(Map.class))).thenThrow(new RuntimeException("caída"));
        mvc.perform(multipart("/api/transcribir").file(new MockMultipartFile("audio", "a.m4a", "audio/mp4", new byte[2000]))
                        .header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value(""))
                .andExpect(jsonPath("$.error").value("No se pudo transcribir el audio. Revisa tu conexión e inténtalo de nuevo."));
    }

    @Test
    void unAudioVacioNoLlegaALaApi() throws Exception {
        mvc.perform(multipart("/api/transcribir").file(new MockMultipartFile("audio", "a.m4a", "audio/mp4", new byte[0]))
                        .header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("No llegó el audio. Vuelve a grabarlo."));
        verify(restTemplate, never()).postForObject(anyString(), org.mockito.ArgumentMatchers.any(), eq(Map.class));
    }
}
