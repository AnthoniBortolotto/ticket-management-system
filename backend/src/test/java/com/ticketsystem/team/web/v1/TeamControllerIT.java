package com.ticketsystem.team.web.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ticketsystem.support.AuthTokens;
import com.ticketsystem.support.IntegrationTest;
import com.ticketsystem.support.SecureMockMvc;
import com.ticketsystem.support.TeamBuilder;
import com.ticketsystem.support.UserBuilder;
import com.ticketsystem.user.domain.User;
import jakarta.persistence.EntityManager;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/**
 * Gestao de equipes pelo endpoint, com tokens reais, a cadeia de seguranca e o banco.
 *
 * <p>O elenco, montado a cada teste: um admin que nao participa da equipe, o lider dela,
 * um membro comum, um agente de <em>outra</em> equipe, um agente sem equipe para ser
 * adicionado e um solicitante.
 *
 * <p>As assercoes saem da regra documentada no {@code TeamService}, nao do codigo dele. Os
 * casos negativos olham o banco depois da recusa: um 403 que grava antes de recusar passaria
 * num teste que so conferisse o status.
 */
@IntegrationTest
class TeamControllerIT {

    private static final String PREFIXO = "team-it-";
    private static final long INEXISTENTE = 999_999_999L;

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private EntityManager em;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private JdbcClient jdbc;

    private MockMvc mvc;
    private AuthTokens tokens;

    private User admin;
    private User lider;
    private User membro;
    private User deOutraEquipe;
    private User novato;
    private User solicitante;
    private Long equipe;

    @BeforeEach
    void montarElenco() {
        mvc = SecureMockMvc.from(contexto);
        tokens = new AuthTokens(encoder);

        transacao.executeWithoutResult(s -> {
            admin = UserBuilder.anAdmin().persistIn(em);
            lider = UserBuilder.anAgent().persistIn(em);
            membro = UserBuilder.anAgent().persistIn(em);
            deOutraEquipe = UserBuilder.anAgent().persistIn(em);
            novato = UserBuilder.anAgent().persistIn(em);
            solicitante = UserBuilder.aRequester().persistIn(em);

            equipe = TeamBuilder.aTeam().withLead(lider.getId()).withMember(membro.getId()).persistIn(em).getId();
            TeamBuilder.aTeam().withLead(deOutraEquipe.getId()).persistIn(em);
        });
    }

    @AfterEach
    void limpar() {
        jdbc.sql("DELETE FROM teams WHERE name LIKE :a OR name LIKE :b")
                .param("a", TeamBuilder.PREFIXO + "%").param("b", PREFIXO + "%").update();
        jdbc.sql("DELETE FROM users WHERE email LIKE :p").param("p", UserBuilder.PREFIXO + "%").update();
    }

    // --- criar equipe -------------------------------------------------------------------

    @Test
    @DisplayName("admin cria equipe, que nasce sem membros")
    void adminCriaEquipe() throws Exception {
        criarEquipe(admin, PREFIXO + "Suporte N2")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value(PREFIXO + "Suporte N2"))
                .andExpect(jsonPath("$.members").isEmpty());
    }

    @Test
    @DisplayName("nome repetido com outra caixa e 409 em ProblemDetail")
    void nomeRepetidoEh409() throws Exception {
        criarEquipe(admin, PREFIXO + "Repetida").andExpect(status().isCreated());

        criarEquipe(admin, PREFIXO.toUpperCase(Locale.ROOT) + "REPETIDA")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("nome no limite de 100 caracteres entra; 101 e recusado antes do banco")
    void larguraDoNomeEhContrato() throws Exception {
        criarEquipe(admin, PREFIXO + "n".repeat(100 - PREFIXO.length())).andExpect(status().isCreated());
        criarEquipe(admin, PREFIXO + "n".repeat(101 - PREFIXO.length()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @DisplayName("nem lider nem solicitante criam equipe")
    void naoAdminNaoCriaEquipe() throws Exception {
        criarEquipe(lider, PREFIXO + "Do Lider").andExpect(status().isForbidden());
        criarEquipe(solicitante, PREFIXO + "Do Solicitante").andExpect(status().isForbidden());

        assertThat(jdbc.sql("SELECT count(*) FROM teams WHERE name LIKE :p")
                        .param("p", PREFIXO + "%").query(Long.class).single())
                .isZero();
    }

    // --- consultar ----------------------------------------------------------------------

    @Test
    @DisplayName("membro ve a equipe e os vinculos, na ordem de entrada")
    void membroVeAEquipe() throws Exception {
        mvc.perform(como(membro, get("/api/v1/teams/{id}", equipe)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[0].userId").value(lider.getId()))
                .andExpect(jsonPath("$.members[0].role").value("LEAD"))
                .andExpect(jsonPath("$.members[1].userId").value(membro.getId()))
                .andExpect(jsonPath("$.members[1].role").value("MEMBER"));
    }

    @Test
    @DisplayName("admin ve a equipe sem participar dela")
    void adminVeAEquipe() throws Exception {
        mvc.perform(como(admin, get("/api/v1/teams/{id}", equipe))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("agente de outra equipe recebe 404 com o mesmo corpo de uma equipe inexistente")
    void deForaNaoDistingueEquipeInvisivelDeInexistente() throws Exception {
        // Se os dois corpos diferissem em qualquer coisa alem do caminho, a diferenca seria
        // um oraculo: bastaria varrer ids para descobrir quais equipes existem.
        String invisivel = mvc.perform(como(deOutraEquipe, get("/api/v1/teams/{id}", equipe)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String inexistente = mvc.perform(como(deOutraEquipe, get("/api/v1/teams/{id}", INEXISTENTE)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        for (String campo : new String[] {"$.status", "$.title", "$.detail"}) {
            assertThat((Object) JsonPath.read(invisivel, campo)).isEqualTo(JsonPath.read(inexistente, campo));
        }
    }

    @Test
    @DisplayName("solicitante nao enxerga equipe nenhuma")
    void solicitanteNaoVe() throws Exception {
        mvc.perform(como(solicitante, get("/api/v1/teams/{id}", equipe))).andExpect(status().isNotFound());
    }

    // --- adicionar membro ---------------------------------------------------------------

    @Test
    @DisplayName("lider adiciona um agente, que entra como membro e aparece na equipe")
    void liderAdicionaMembro() throws Exception {
        adicionar(lider, novato.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(novato.getId()))
                .andExpect(jsonPath("$.role").value("MEMBER"));

        mvc.perform(como(lider, get("/api/v1/teams/{id}", equipe)))
                .andExpect(jsonPath("$.members[2].userId").value(novato.getId()));
    }

    @Test
    @DisplayName("membro comum nao gerencia membros: 403, e nada gravado")
    void membroComumNaoAdiciona() throws Exception {
        // O caso negativo que o roadmap pede para a fase: quem nao e admin nem lider nao
        // gerencia membros.
        adicionar(membro, novato.getId())
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(vinculosDe(novato)).isZero();
    }

    @Test
    @DisplayName("lider de outra equipe nao gerencia esta: 404, e nada gravado")
    void liderDeOutraEquipeNaoAdiciona() throws Exception {
        // Ser LEAD nao e um papel global. Liderar a propria equipe nao da poder nenhum sobre
        // as outras — e a resposta nao confirma nem que esta existe.
        adicionar(deOutraEquipe, novato.getId()).andExpect(status().isNotFound());

        assertThat(vinculosDe(novato)).isZero();
    }

    @Test
    @DisplayName("solicitante nao gerencia equipe nenhuma")
    void solicitanteNaoAdiciona() throws Exception {
        adicionar(solicitante, novato.getId()).andExpect(status().isNotFound());

        assertThat(vinculosDe(novato)).isZero();
    }

    @Test
    @DisplayName("solicitante nao entra em equipe: 409")
    void solicitanteNaoEntraEmEquipe() throws Exception {
        adicionar(admin, solicitante.getId()).andExpect(status().isConflict());

        assertThat(vinculosDe(solicitante)).isZero();
    }

    @Test
    @DisplayName("quem ja e membro nao entra de novo: 409")
    void jaMembroEh409() throws Exception {
        adicionar(lider, membro.getId()).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("usuario que nao existe e 404")
    void usuarioInexistenteEh404() throws Exception {
        adicionar(admin, INEXISTENTE).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("pedido sem userId e 400 com o campo apontado")
    void semUserIdEh400() throws Exception {
        mvc.perform(como(admin, post("/api/v1/teams/{id}/members", equipe))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("userId"));
    }

    // --- lideranca ----------------------------------------------------------------------

    @Test
    @DisplayName("lider nao promove ninguem: 403, e o papel nao muda")
    void liderNaoPromove() throws Exception {
        mudarPapel(lider, membro.getId(), "LEAD").andExpect(status().isForbidden());

        assertThat(papelDe(membro)).isEqualTo("MEMBER");
    }

    @Test
    @DisplayName("promovido por admin, o membro passa a gerenciar com o mesmo token")
    void promocaoValeNaHora() throws Exception {
        // A equipe nao esta no token (ADR 0003), entao nao ha o que esperar vencer: o token
        // emitido antes da promocao ja vale como lider na requisicao seguinte.
        String tokenDoMembro = bearer(membro);
        adicionarCom(tokenDoMembro, novato.getId()).andExpect(status().isForbidden());

        mudarPapel(admin, membro.getId(), "LEAD")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("LEAD"));

        adicionarCom(tokenDoMembro, novato.getId()).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("papel fora do enum e 400")
    void papelInvalidoEh400() throws Exception {
        mudarPapel(admin, membro.getId(), "BOSS").andExpect(status().isBadRequest());
        assertThat(papelDe(membro)).isEqualTo("MEMBER");
    }

    // --- remover membro -----------------------------------------------------------------

    @Test
    @DisplayName("removido pelo lider, o membro perde o acesso na hora, com o mesmo token")
    void remocaoValeNaHora() throws Exception {
        String tokenDoMembro = bearer(membro);
        mvc.perform(comToken(tokenDoMembro, get("/api/v1/teams/{id}", equipe))).andExpect(status().isOk());

        mvc.perform(como(lider, delete("/api/v1/teams/{id}/members/{userId}", equipe, membro.getId())))
                .andExpect(status().isNoContent());

        // E a razao de a equipe nao viajar no token: se viajasse, isto continuaria 200 ate
        // o token vencer.
        mvc.perform(comToken(tokenDoMembro, get("/api/v1/teams/{id}", equipe))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("lider nao remove lider, nem a si mesmo; admin remove")
    void soAdminRemoveLider() throws Exception {
        mvc.perform(como(lider, delete("/api/v1/teams/{id}/members/{userId}", equipe, lider.getId())))
                .andExpect(status().isForbidden());
        assertThat(papelDe(lider)).isEqualTo("LEAD");

        mvc.perform(como(admin, delete("/api/v1/teams/{id}/members/{userId}", equipe, lider.getId())))
                .andExpect(status().isNoContent());
        assertThat(vinculosDe(lider)).isZero();
    }

    @Test
    @DisplayName("membro comum nao remove ninguem: 403, e o vinculo continua")
    void membroComumNaoRemove() throws Exception {
        mvc.perform(como(membro, delete("/api/v1/teams/{id}/members/{userId}", equipe, lider.getId())))
                .andExpect(status().isForbidden());

        assertThat(papelDe(lider)).isEqualTo("LEAD");
    }

    @Test
    @DisplayName("remover quem nao participa e 404")
    void removerQuemNaoParticipaEh404() throws Exception {
        mvc.perform(como(lider, delete("/api/v1/teams/{id}/members/{userId}", equipe, novato.getId())))
                .andExpect(status().isNotFound());
    }

    // --- apoio --------------------------------------------------------------------------

    private ResultActions criarEquipe(User ator, String nome) throws Exception {
        return mvc.perform(como(ator, post("/api/v1/teams"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\"}".formatted(nome)));
    }

    private ResultActions adicionar(User ator, Long userId) throws Exception {
        return adicionarCom(bearer(ator), userId);
    }

    private ResultActions adicionarCom(String token, Long userId) throws Exception {
        return mvc.perform(comToken(token, post("/api/v1/teams/{id}/members", equipe))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":%d}".formatted(userId)));
    }

    private ResultActions mudarPapel(User ator, Long userId, String papel) throws Exception {
        return mvc.perform(como(ator, put("/api/v1/teams/{id}/members/{userId}/role", equipe, userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"%s\"}".formatted(papel)));
    }

    private MockHttpServletRequestBuilder como(User ator, MockHttpServletRequestBuilder pedido) {
        return comToken(bearer(ator), pedido);
    }

    private static MockHttpServletRequestBuilder comToken(String token, MockHttpServletRequestBuilder pedido) {
        return pedido.header(HttpHeaders.AUTHORIZATION, token);
    }

    /** Token com o id e o papel reais da pessoa: a regra compara esse id com o banco. */
    private String bearer(User pessoa) {
        return "Bearer " + tokens.valid(pessoa.getId(), pessoa.getRole());
    }

    private long vinculosDe(User pessoa) {
        return jdbc.sql("SELECT count(*) FROM team_memberships WHERE user_id = :u")
                .param("u", pessoa.getId()).query(Long.class).single();
    }

    private String papelDe(User pessoa) {
        return jdbc.sql("SELECT role FROM team_memberships WHERE team_id = :t AND user_id = :u")
                .param("t", equipe).param("u", pessoa.getId()).query(String.class).single();
    }
}
