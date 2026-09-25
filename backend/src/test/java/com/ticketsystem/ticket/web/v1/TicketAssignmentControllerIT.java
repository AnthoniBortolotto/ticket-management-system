package com.ticketsystem.ticket.web.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketsystem.support.AuthTokens;
import com.ticketsystem.support.IntegrationTest;
import com.ticketsystem.support.SecureMockMvc;
import com.ticketsystem.support.TeamBuilder;
import com.ticketsystem.support.TicketBuilder;
import com.ticketsystem.support.UserBuilder;
import com.ticketsystem.user.domain.User;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.BooleanSupplier;
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
 * Atribuicao pelo endpoint, com tokens reais e o banco.
 *
 * <p>O elenco: Ana abre o ticket, que cai no Suporte. Carla lidera o Suporte; Diego e Marina
 * sao membros. Helena lidera a Infra, e Igor e membro dela. Um admin nao participa de nada.
 *
 * <p>Cada movimento e seguido da pergunta que ele muda: <em>quem ve o ticket agora?</em> E a
 * consequencia que importa — mandar para o exclusivo que nao tira o acesso da equipe, ou
 * transferir que deixa a equipe antiga vendo, seria vazamento com cara de funcionalidade.
 */
@IntegrationTest
class TicketAssignmentControllerIT {

    private static final Duration LIMITE_DO_LISTENER = Duration.ofSeconds(15);

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

    private User admin, ana, carla, diego, marina, helena, igor;
    private Long suporte, infra, ticket;

    @BeforeEach
    void montarElenco() {
        mvc = SecureMockMvc.from(contexto);
        tokens = new AuthTokens(encoder);
        transacao.executeWithoutResult(s -> {
            admin = UserBuilder.anAdmin().persistIn(em);
            ana = UserBuilder.aRequester().persistIn(em);
            carla = UserBuilder.anAgent().persistIn(em);
            diego = UserBuilder.anAgent().persistIn(em);
            marina = UserBuilder.anAgent().persistIn(em);
            helena = UserBuilder.anAgent().persistIn(em);
            igor = UserBuilder.anAgent().persistIn(em);
            suporte = TeamBuilder.aTeam().withLead(carla.getId()).withMember(diego.getId())
                    .withMember(marina.getId()).persistIn(em).getId();
            infra = TeamBuilder.aTeam().withLead(helena.getId()).withMember(igor.getId()).persistIn(em).getId();
            ticket = TicketBuilder.aTicket().requestedBy(ana.getId()).assignedToTeam(suporte).persistIn(em).getId();
        });
    }

    @AfterEach
    void limpar() {
        jdbc.sql("DELETE FROM tickets WHERE requester_id IN (SELECT id FROM users WHERE email LIKE :p)")
                .param("p", UserBuilder.PREFIXO + "%").update();
        jdbc.sql("DELETE FROM users WHERE email LIKE :p").param("p", UserBuilder.PREFIXO + "%").update();
        jdbc.sql("DELETE FROM teams WHERE name LIKE :p").param("p", TeamBuilder.PREFIXO + "%").update();
    }

    // --- responsavel atual ----------------------------------------------------------------

    @Test
    @DisplayName("um membro designa outro membro como responsavel, e o ticket continua da equipe")
    void membroDesignaMembro() throws Exception {
        designar(marina, diego.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentAssigneeId").value(diego.getId()))
                .andExpect(jsonPath("$.assignedTeamId").value(suporte));

        assertThat(atribuicao()).containsEntry("current_assignee_id", diego.getId());
    }

    @Test
    @DisplayName("responsavel de fora da equipe: 400, e nada muda")
    void responsavelDeForaEh400() throws Exception {
        designar(marina, igor.getId()).andExpect(status().isBadRequest());

        assertThat(atribuicao().get("current_assignee_id")).isNull();
    }

    @Test
    @DisplayName("solicitante nao designa ninguem: 403; agente de outra equipe nem ve: 404")
    void quemNaoAtendeNaoDesigna() throws Exception {
        designar(ana, diego.getId()).andExpect(status().isForbidden());
        designar(igor, igor.getId()).andExpect(status().isNotFound());

        assertThat(atribuicao().get("current_assignee_id")).isNull();
    }

    @Test
    @DisplayName("quem sai da equipe deixa de ser o responsavel atual dos tickets dela")
    void saidaDaEquipeLimpaOResponsavel() throws Exception {
        designar(marina, diego.getId()).andExpect(status().isOk());

        mvc.perform(como(carla, delete("/api/v1/teams/{t}/members/{u}", suporte, diego.getId())))
                .andExpect(status().isNoContent());

        // O listener roda depois do commit, em outra thread: esperar, e nao afirmar na hora.
        aguardarAte(() -> atribuicao().get("current_assignee_id") == null);
        assertThat(atribuicao()).containsEntry("assigned_team_id", suporte);
    }

    // --- modo exclusivo -------------------------------------------------------------------

    @Test
    @DisplayName("membro comum nao manda para o exclusivo: 403, e o ticket continua da equipe")
    void membroNaoMandaParaExclusivo() throws Exception {
        exclusivo(marina, diego.getId()).andExpect(status().isForbidden());

        assertThat(atribuicao()).containsEntry("assigned_team_id", suporte);
    }

    @Test
    @DisplayName("exclusivo para alguem de fora da equipe: 400")
    void exclusivoParaDeForaEh400() throws Exception {
        exclusivo(carla, igor.getId()).andExpect(status().isBadRequest());

        assertThat(atribuicao()).containsEntry("assigned_team_id", suporte);
    }

    @Test
    @DisplayName("mandado para o exclusivo, o ticket some da equipe e fica com o responsavel e com a lider")
    void exclusivoTiraOAcessoDaEquipe() throws Exception {
        exclusivo(carla, diego.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exclusiveAssigneeId").value(diego.getId()))
                .andExpect(jsonPath("$.originTeamId").value(suporte))
                .andExpect(jsonPath("$.assignedTeamId").doesNotExist());

        // Marina era da equipe e perde o acesso — pela URL e na lista.
        mvc.perform(como(marina, get("/api/v1/tickets/{id}", ticket))).andExpect(status().isNotFound());
        mvc.perform(como(marina, get("/api/v1/tickets")))
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(ticket)).isEmpty());
        for (User quemVe : new User[] {diego, carla, ana, admin}) {
            mvc.perform(como(quemVe, get("/api/v1/tickets/{id}", ticket))).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("a lider da origem atua no ticket exclusivo: move o status e escreve nota interna")
    void liderDaOrigemAtua() throws Exception {
        exclusivo(carla, diego.getId()).andExpect(status().isOk());

        mvc.perform(como(carla, post("/api/v1/tickets/{id}/transitions", ticket))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
        mvc.perform(como(carla, post("/api/v1/tickets/{id}/comments", ticket))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Acompanhando\",\"internal\":true}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("o responsavel exclusivo devolve, e a equipe volta a ver")
    void responsavelDevolve() throws Exception {
        exclusivo(carla, diego.getId()).andExpect(status().isOk());

        mvc.perform(como(diego, post("/api/v1/tickets/{id}/assignment/return", ticket)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTeamId").value(suporte))
                .andExpect(jsonPath("$.originTeamId").doesNotExist());

        mvc.perform(como(marina, get("/api/v1/tickets/{id}", ticket))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("quem so via o ticket pela equipe nao o devolve depois que ele saiu: 404")
    void exMembroNaoDevolve() throws Exception {
        exclusivo(carla, diego.getId()).andExpect(status().isOk());

        mvc.perform(como(marina, post("/api/v1/tickets/{id}/assignment/return", ticket)))
                .andExpect(status().isNotFound());
        assertThat(atribuicao()).containsEntry("exclusive_assignee_id", diego.getId());
    }

    // --- transferencia --------------------------------------------------------------------

    @Test
    @DisplayName("a lider transfere para outra equipe, e a equipe antiga perde o acesso na hora")
    void transferenciaTrocaQuemVe() throws Exception {
        designar(marina, diego.getId()).andExpect(status().isOk());

        transferir(carla, infra)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTeamId").value(infra))
                .andExpect(jsonPath("$.currentAssigneeId").doesNotExist());

        mvc.perform(como(diego, get("/api/v1/tickets/{id}", ticket))).andExpect(status().isNotFound());
        mvc.perform(como(igor, get("/api/v1/tickets/{id}", ticket))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("membro comum nao transfere: 403; equipe inexistente: 400")
    void transferenciaRecusada() throws Exception {
        transferir(marina, infra).andExpect(status().isForbidden());
        transferir(carla, 999_999_999L).andExpect(status().isBadRequest());

        assertThat(atribuicao()).containsEntry("assigned_team_id", suporte);
    }

    // --- apoio ----------------------------------------------------------------------------

    private ResultActions designar(User quem, Long responsavel) throws Exception {
        return mvc.perform(como(quem, put("/api/v1/tickets/{id}/assignment/current", ticket))
                .contentType(MediaType.APPLICATION_JSON).content("{\"assigneeId\":%d}".formatted(responsavel)));
    }

    private ResultActions exclusivo(User quem, Long responsavel) throws Exception {
        return mvc.perform(como(quem, post("/api/v1/tickets/{id}/assignment/exclusive", ticket))
                .contentType(MediaType.APPLICATION_JSON).content("{\"assigneeId\":%d}".formatted(responsavel)));
    }

    private ResultActions transferir(User quem, Long equipe) throws Exception {
        return mvc.perform(como(quem, post("/api/v1/tickets/{id}/assignment/transfer", ticket))
                .contentType(MediaType.APPLICATION_JSON).content("{\"teamId\":%d}".formatted(equipe)));
    }

    private MockHttpServletRequestBuilder como(User quem, MockHttpServletRequestBuilder pedido) {
        return pedido.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.valid(quem.getId(), quem.getRole()));
    }

    private Map<String, Object> atribuicao() {
        return jdbc.sql("""
                        SELECT assigned_team_id, current_assignee_id, exclusive_assignee_id, origin_team_id
                        FROM tickets WHERE id = :id
                        """)
                .param("id", ticket).query().singleRow();
    }

    private static void aguardarAte(BooleanSupplier condicao) throws InterruptedException {
        Instant limite = Instant.now().plus(LIMITE_DO_LISTENER);
        while (Instant.now().isBefore(limite)) {
            if (condicao.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("o listener nao limpou o responsavel atual a tempo");
    }
}
