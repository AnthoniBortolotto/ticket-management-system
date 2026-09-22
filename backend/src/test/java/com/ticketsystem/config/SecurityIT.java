package com.ticketsystem.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketsystem.support.AuthTokens;
import com.ticketsystem.support.IntegrationTest;
import com.ticketsystem.support.SecureMockMvc;
import com.ticketsystem.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

/**
 * A cadeia de filtros inteira, com tokens de verdade.
 *
 * <p>As assercoes vem da regra do roadmap, e nao do codigo que existe: token expirado e
 * 401; assinatura invalida e 401; token sem o papel exigido e 403; rota publica sem token
 * responde. Mais o caso negativo de cada uma — sem ele, uma regra frouxa passaria verde.
 *
 * <p><strong>Nenhum {@code @WithMockUser} aqui.</strong> Ele injeta a permissao direto no
 * contexto de seguranca e pula justamente a conversao do claim {@code role} em
 * {@code ROLE_ADMIN}, que e o pedaco que quebra. Todo token abaixo e assinado e passa pelo
 * decoder real.
 *
 * <p>Todo 401 e 403 e conferido tambem pelo {@code Content-Type}: olhar so o status deixaria
 * o teste verde com o contrato de erro quebrado.
 */
@IntegrationTest
class SecurityIT {

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private FilterChainProxy cadeia;

    private MockMvc mvc;
    private AuthTokens tokens;
    private long idDoAdmin;

    @BeforeEach
    void configurar() {
        mvc = SecureMockMvc.from(contexto);
        tokens = new AuthTokens(encoder);
        idDoAdmin = jdbc.sql("SELECT id FROM users WHERE email = 'admin@ticketsystem.local'")
                .query(Long.class)
                .single();
    }

    // ------------------------------------------------------------ o que e publico

    @ParameterizedTest(name = "GET {0} responde sem token")
    @ValueSource(strings = {"/actuator/health", "/v3/api-docs", "/swagger-ui.html"})
    @DisplayName("sonda de saude e documentacao sao publicas")
    void rotasPublicasRespondemSemToken(String caminho) throws Exception {
        int status = mvc.perform(get(caminho)).andReturn().getResponse().getStatus();

        // O swagger redireciona (3xx) e os outros respondem 200; o que importa e nao ser
        // recusado pela seguranca.
        assertThat(status).isNotIn(401, 403);
    }

    @ParameterizedTest(name = "POST {0} chega ao controller sem token")
    @ValueSource(strings = {"/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout"})
    @DisplayName("os endpoints de sessao sao publicos")
    void endpointsDeSessaoSaoPublicos(String caminho) throws Exception {
        // Corpo vazio de proposito: o 400 de validacao so pode vir do controller, entao
        // prova que a cadeia de filtros deixou passar. Um 401 aqui significaria o
        // contrario — e credenciais erradas tambem dariam 401, o que tornaria o teste inutil.
        mvc.perform(post(caminho).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------- o que NAO e publico

    @Test
    @DisplayName("rota protegida sem token e 401, no formato de erro da API")
    void rotaProtegidaSemTokenEh401() throws Exception {
        esperarNaoAutenticado(mvc.perform(get("/api/v1/users/" + idDoAdmin)))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @ParameterizedTest(name = "GET {0} sem token e 401")
    @ValueSource(strings = {"/api/v1/qualquer-coisa", "/api/v1/auth/qualquer-outra", "/api/v1/auth/login"})
    @DisplayName("o que nao foi liberado explicitamente exige token")
    void oQueNaoFoiLiberadoExigeToken(String caminho) throws Exception {
        // Os tres casos que uma regra frouxa deixaria passar com o resto da suite verde:
        //  - rota que nao existe: sem token, e 401 antes de ser 404 — nao se confirma o
        //    que existe a quem nao se identificou;
        //  - /api/v1/auth/qualquer-outra: pegaria um curinga /api/v1/auth/**;
        //  - GET /api/v1/auth/login: a liberacao e so para POST.
        esperarNaoAutenticado(mvc.perform(get(caminho)));
    }

    // ------------------------------------------------------ tokens que nao valem

    @Test
    @DisplayName("token expirado e 401")
    void tokenExpiradoEh401() throws Exception {
        esperarNaoAutenticado(comToken(tokens.expired(idDoAdmin, UserRole.ADMIN)));
    }

    @Test
    @DisplayName("token assinado com outra chave e 401")
    void tokenDeOutraChaveEh401() throws Exception {
        esperarNaoAutenticado(comToken(AuthTokens.signedWithOtherKey(idDoAdmin, UserRole.ADMIN)));
    }

    @Test
    @DisplayName("promover-se a admin editando o payload e 401")
    void payloadAdulteradoEh401() throws Exception {
        String deSolicitante = tokens.valid(idDoAdmin, UserRole.REQUESTER);
        String adulterado = AuthTokens.withRoleSwapped(deSolicitante, UserRole.REQUESTER, UserRole.ADMIN);

        // O ataque que importa: se o decoder so parseasse o JWT sem conferir a assinatura,
        // qualquer conta de solicitante viraria admin trocando uma palavra.
        esperarNaoAutenticado(comToken(adulterado));
    }

    @Test
    @DisplayName("token que se declara sem assinatura e 401")
    void tokenSemAssinaturaEh401() throws Exception {
        esperarNaoAutenticado(comToken(AuthTokens.unsigned(idDoAdmin, UserRole.ADMIN)));
    }

    @Test
    @DisplayName("a mensagem interna da recusa nao vaza para a resposta")
    void mensagemInternaNaoVaza() throws Exception {
        // O Nimbus explica a recusa ("Jwt expired at 2026-..."). Isso ajuda quem ataca a
        // calibrar a proxima tentativa e nao ajuda quem integra.
        comToken(tokens.expired(idDoAdmin, UserRole.ADMIN))
                .andExpect(jsonPath("$.detail").value("Credenciais ausentes ou invalidas."));
    }

    // ---------------------------------------------------------- papel e permissao

    @ParameterizedTest(name = "{0} autenticado mas sem permissao e 403")
    @ValueSource(strings = {"REQUESTER", "AGENT"})
    @DisplayName("token valido sem o papel exigido e 403, e nao 401")
    void papelInsuficienteEh403(UserRole papel) throws Exception {
        // 403 e "sei quem voce e, e voce nao pode"; 401 e "nao sei quem voce e". Um token
        // valido que recebesse 401 aqui indicaria que o conversor de papel nao esta ligado.
        comToken(tokens.valid(idDoAdmin, papel))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("o mesmo endpoint responde para quem tem o papel")
    void papelCertoEh200() throws Exception {
        // Sem este par, o 403 acima passaria tambem com o conversor quebrado — quando todo
        // mundo, inclusive o admin, recebe 403.
        comToken(tokens.valid(idDoAdmin, UserRole.ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@ticketsystem.local"));
    }

    // ---------------------------------------------------------------- a fiacao

    @Test
    @DisplayName("o filtro de bearer token esta na cadeia")
    void filtroDeBearerEstaNaCadeia() {
        // O log do JwtConfig diz que o HS256 subiu; isto e o que quebra o build se um dia
        // deixar de subir. Log convence gente, assercao quebra build.
        assertThat(cadeia.getFilterChains())
                .flatMap(chain -> chain.getFilters())
                .anyMatch(BearerTokenAuthenticationFilter.class::isInstance);
    }

    private ResultActions comToken(String token) throws Exception {
        return mvc.perform(get("/api/v1/users/" + idDoAdmin).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static ResultActions esperarNaoAutenticado(ResultActions resposta) throws Exception {
        return resposta
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }
}
