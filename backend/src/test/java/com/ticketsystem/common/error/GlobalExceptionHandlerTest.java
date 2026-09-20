package com.ticketsystem.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * O formato de erro e contrato: o frontend tem um unico caminho de tratamento e o
 * springdoc o publica no schema. Este teste fixa esse formato a partir de um controller
 * de mentira, porque o formato precisa estar certo antes do primeiro controller de
 * verdade existir.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void configurar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ControllerDeTeste())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void validacaoFalhaComProblemDetailEAListaDeCamposRejeitados() throws Exception {
        mockMvc.perform(post("/exemplo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titulo\":\"  \",\"prioridade\":-1}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.title").value("Requisicao invalida"))
            // Ordenado por campo: a resposta nao pode variar entre chamadas iguais.
            .andExpect(jsonPath("$.errors[0].field").value("prioridade"))
            .andExpect(jsonPath("$.errors[1].field").value("titulo"))
            .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void metodoNaoSuportadoTambemSaiEmProblemDetail() throws Exception {
        mockMvc.perform(get("/exemplo"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(405));
    }

    @RestController
    static class ControllerDeTeste {

        @PostMapping("/exemplo")
        void receber(@Valid @RequestBody CorpoDeTeste corpo) {
            // O controller nao precisa fazer nada: o que se testa e a rejeicao antes dele.
        }
    }

    record CorpoDeTeste(@NotBlank String titulo, @Positive int prioridade) {
    }
}
