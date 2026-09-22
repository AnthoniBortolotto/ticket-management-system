package com.ticketsystem.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketsystem.support.IntegrationTest;
import com.ticketsystem.support.SecureMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * O que o contrato publicado diz sobre autenticacao.
 *
 * <p>O schema em {@code /v3/api-docs} e a fonte dos tipos TypeScript do frontend. Se ele
 * disser que o login exige token, o cliente gerado vai mandar um — e a tela quebra com o
 * compilador verde. A cadeia de filtros pode estar certa e o contrato errado; este teste
 * olha o contrato.
 */
@IntegrationTest
class OpenApiContractIT {

    @Autowired
    private WebApplicationContext contexto;

    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        mvc = SecureMockMvc.from(contexto);
    }

    @ParameterizedTest(name = "{0} e publico no contrato")
    @ValueSource(strings = {"/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout"})
    @DisplayName("os endpoints de sessao nao exigem bearer no schema")
    void endpointsDeSessaoSaoPublicosNoContrato(String caminho) throws Exception {
        // `security: []` na operacao e o que anula o requisito global do OpenApiConfig.
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['%s'].post.security".formatted(caminho)).isEmpty());
    }

    @Test
    @DisplayName("rota protegida herda o requisito global de bearer")
    void rotaProtegidaExigeBearerNoContrato() throws Exception {
        // O par do teste acima: sem ele, um @SecurityRequirements vazio espalhado por
        // engano deixaria o contrato inteiro dizendo que nada exige token.
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.security[0]['bearer-jwt']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/{id}'].get.security").doesNotExist());
    }
}
