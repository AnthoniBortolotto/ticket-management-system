package com.ticketsystem.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @ParameterizedTest(name = "{0} vira HTTP {1}")
    @CsvSource({
        "INVALID, 400",
        "UNAUTHORIZED, 401",
        "FORBIDDEN, 403",
        "NOT_FOUND, 404",
        "CONFLICT, 409",
        "LOCKED, 423"
    })
    @DisplayName("excecao de dominio vira ProblemDetail com o status do tipo de problema")
    void excecaoDeDominioViraProblemDetail(ProblemKind tipo, int statusEsperado) throws Exception {
        mockMvc.perform(get("/dominio/" + tipo.name()))
            .andExpect(status().is(statusEsperado))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(statusEsperado))
            .andExpect(jsonPath("$.title").value("Titulo de teste"))
            .andExpect(jsonPath("$.detail").value("Detalhe de teste"));
    }

    @Test
    @DisplayName("falha de autenticacao sai no mesmo formato do resto da API")
    void falhaDeAutenticacaoSaiEmProblemDetail() throws Exception {
        // 401 e 403 nascem dentro da cadeia de filtros do Spring Security, antes do
        // DispatcherServlet, entao por padrao NAO passam por este advice e saem num
        // formato diferente de toda a API. O SecurityConfig os devolve para o
        // HandlerExceptionResolver justamente para caírem aqui.
        mockMvc.perform(get("/erro/autenticacao"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("acesso negado sai no mesmo formato do resto da API")
    void acessoNegadoSaiEmProblemDetail() throws Exception {
        mockMvc.perform(get("/erro/acesso"))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("a mensagem da excecao nao vaza para o corpo da resposta")
    void mensagemInternaNaoVazaNoCorpo() throws Exception {
        // O `detail` e escrito para quem consome a API. A mensagem de uma excecao de
        // autenticacao descreve o mecanismo interno ("Bad credentials", nome de classe,
        // as vezes o identificador tentado) e nao pode virar corpo de resposta.
        mockMvc.perform(get("/erro/autenticacao"))
            .andExpect(jsonPath("$.detail").value(
                Matchers.not(Matchers.containsString("segredo-interno"))));
    }

    @RestController
    static class ControllerDeTeste {

        @PostMapping("/exemplo")
        void receber(@Valid @RequestBody CorpoDeTeste corpo) {
            // O controller nao precisa fazer nada: o que se testa e a rejeicao antes dele.
        }

        @GetMapping("/dominio/{tipo}")
        void lancarDeDominio(@PathVariable ProblemKind tipo) {
            throw new ExcecaoDeTeste(tipo);
        }

        @GetMapping("/erro/autenticacao")
        void lancarNaoAutenticado() {
            throw new BadCredentialsException("segredo-interno: usuario nao encontrado");
        }

        @GetMapping("/erro/acesso")
        void lancarAcessoNegado() {
            throw new AccessDeniedException("segredo-interno: falta ROLE_ADMIN");
        }
    }

    /**
     * Subclasse declarada aqui dentro de proposito: prova que o advice traduz qualquer
     * {@link DomainException} sem que {@code common} precise conhecer modulo nenhum. Se o
     * teste importasse uma excecao de {@code auth} ou de {@code user}, o
     * {@code ModularityTest} quebraria o build por ciclo entre modulos.
     */
    static final class ExcecaoDeTeste extends DomainException {

        ExcecaoDeTeste(ProblemKind tipo) {
            super(tipo, "Titulo de teste", "Detalhe de teste");
        }
    }

    record CorpoDeTeste(@NotBlank String titulo, @Positive int prioridade) {
    }
}
