package com.ticketsystem.ticket.web.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketsystem.support.AuthTokens;
import com.ticketsystem.support.IntegrationTest;
import com.ticketsystem.support.SecureMockMvc;
import com.ticketsystem.support.TeamBuilder;
import com.ticketsystem.support.UserBuilder;
import com.ticketsystem.user.domain.User;
import jakarta.persistence.EntityManager;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/** O roteamento de categoria para equipe pelo endpoint. So admin configura. */
@IntegrationTest
class TicketRouteControllerIT {

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
    private User agente;
    private User solicitante;
    private Long suporte;
    private Long infra;

    @BeforeEach
    void montar() {
        mvc = SecureMockMvc.from(contexto);
        tokens = new AuthTokens(encoder);
        jdbc.sql("DELETE FROM ticket_routes").update();
        transacao.executeWithoutResult(s -> {
            admin = UserBuilder.anAdmin().persistIn(em);
            agente = UserBuilder.anAgent().persistIn(em);
            solicitante = UserBuilder.aRequester().persistIn(em);
            suporte = TeamBuilder.aTeam().persistIn(em).getId();
            infra = TeamBuilder.aTeam().persistIn(em).getId();
        });
    }

    @AfterEach
    void limpar() {
        jdbc.sql("DELETE FROM ticket_routes").update();
        jdbc.sql("DELETE FROM users WHERE email LIKE :p").param("p", UserBuilder.PREFIXO + "%").update();
        jdbc.sql("DELETE FROM teams WHERE name LIKE :p").param("p", TeamBuilder.PREFIXO + "%").update();
    }

    @Test
    @DisplayName("admin roteia uma categoria, e a rota aparece na lista")
    void adminRoteia() throws Exception {
        rotear(admin, "ACCESS", infra)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("ACCESS"))
                .andExpect(jsonPath("$.teamId").value(infra));

        mvc.perform(get("/api/v1/ticket-routes").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("ACCESS"))
                .andExpect(jsonPath("$[0].teamId").value(infra));
    }

    @Test
    @DisplayName("rotear de novo redireciona, sem criar uma segunda rota")
    void rotearDeNovoRedireciona() throws Exception {
        rotear(admin, "ACCESS", infra).andExpect(status().isOk());
        rotear(admin, "ACCESS", suporte).andExpect(status().isOk()).andExpect(jsonPath("$.teamId").value(suporte));

        assertThat(jdbc.sql("SELECT team_id FROM ticket_routes WHERE category = 'ACCESS'").query(Long.class).list())
                .containsExactly(suporte);
    }

    @Test
    @DisplayName("equipe que nao existe e 400")
    void equipeInexistenteEh400() throws Exception {
        rotear(admin, "ACCESS", 999_999_999L).andExpect(status().isBadRequest());
        assertThat(rotas()).isZero();
    }

    @Test
    @DisplayName("categoria que nao existe e 400")
    void categoriaInexistenteEh400() throws Exception {
        rotear(admin, "NETWORK", infra).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("agente e solicitante nao roteiam nem leem o roteamento: 403, e nada e gravado")
    void naoAdminNaoRoteia() throws Exception {
        for (User quem : new User[] {agente, solicitante}) {
            rotear(quem, "ACCESS", infra).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/ticket-routes").header(HttpHeaders.AUTHORIZATION, bearer(quem)))
                    .andExpect(status().isForbidden());
        }
        assertThat(rotas()).isZero();
    }

    private ResultActions rotear(User quem, String categoria, Long equipe) throws Exception {
        return mvc.perform(put("/api/v1/ticket-routes/{categoria}", categoria)
                .header(HttpHeaders.AUTHORIZATION, bearer(quem))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamId\":%d}".formatted(equipe)));
    }

    private String bearer(User pessoa) {
        return "Bearer " + tokens.valid(pessoa.getId(), pessoa.getRole());
    }

    private long rotas() {
        return jdbc.sql("SELECT count(*) FROM ticket_routes").query(Long.class).single();
    }
}
