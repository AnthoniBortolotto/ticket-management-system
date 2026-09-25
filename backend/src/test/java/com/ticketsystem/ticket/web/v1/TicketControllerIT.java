package com.ticketsystem.ticket.web.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ticketsystem.support.AuthTokens;
import com.ticketsystem.support.IntegrationTest;
import com.ticketsystem.support.SecureMockMvc;
import com.ticketsystem.support.TeamBuilder;
import com.ticketsystem.support.TicketBuilder;
import com.ticketsystem.support.UserBuilder;
import com.ticketsystem.ticket.TicketStatus;
import com.ticketsystem.ticket.domain.Comment;
import com.ticketsystem.ticket.domain.TicketCategory;
import com.ticketsystem.ticket.domain.TicketRoute;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/**
 * Tickets pelo endpoint, com tokens reais, a cadeia de seguranca e o banco.
 *
 * <p>O elenco: Ana abre o ticket; Bruno e outro solicitante; Diego e membro da equipe que
 * recebe a categoria {@code ACCESS}; Igor e membro de outra equipe; e um admin que nao
 * participa de nenhuma. {@code HARDWARE} fica sem rota de proposito.
 *
 * <p>Cada caso negativo confere o banco depois da recusa. E o CLAUDE.md: toda regra de
 * visibilidade tem teste de integracao do caso negativo, porque ticket de outra equipe na
 * tela e vazamento de dados, nao bug de tela.
 */
@IntegrationTest
class TicketControllerIT {

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
    private User ana;
    private User bruno;
    private User diego;
    private User igor;
    private Long equipe;

    @BeforeEach
    void montarElenco() {
        mvc = SecureMockMvc.from(contexto);
        tokens = new AuthTokens(encoder);
        // O roteamento e global: uma rota deixada por outro teste mudaria para onde o ticket
        // vai. Em teste nao ha seed, entao apagar tudo e seguro.
        jdbc.sql("DELETE FROM ticket_routes").update();

        transacao.executeWithoutResult(s -> {
            admin = UserBuilder.anAdmin().persistIn(em);
            ana = UserBuilder.aRequester().persistIn(em);
            bruno = UserBuilder.aRequester().persistIn(em);
            diego = UserBuilder.anAgent().persistIn(em);
            igor = UserBuilder.anAgent().persistIn(em);
            equipe = TeamBuilder.aTeam().withMember(diego.getId()).persistIn(em).getId();
            Long outraEquipe = TeamBuilder.aTeam().withMember(igor.getId()).persistIn(em).getId();
            em.persist(new TicketRoute(TicketCategory.ACCESS, equipe));
            em.persist(new TicketRoute(TicketCategory.SOFTWARE, outraEquipe));
        });
    }

    @AfterEach
    void limpar() {
        jdbc.sql("DELETE FROM ticket_routes").update();
        jdbc.sql("DELETE FROM tickets WHERE requester_id IN (SELECT id FROM users WHERE email LIKE :p)")
                .param("p", UserBuilder.PREFIXO + "%").update();
        jdbc.sql("DELETE FROM users WHERE email LIKE :p").param("p", UserBuilder.PREFIXO + "%").update();
        jdbc.sql("DELETE FROM teams WHERE name LIKE :p").param("p", TeamBuilder.PREFIXO + "%").update();
    }

    // --- abrir ----------------------------------------------------------------------------

    @Test
    @DisplayName("solicitante abre, e o ticket vai para a equipe da categoria, OPEN")
    void solicitanteAbre() throws Exception {
        abrir(ana, "ACCESS")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.requesterId").value(ana.getId()))
                .andExpect(jsonPath("$.assignedTeamId").value(equipe))
                .andExpect(jsonPath("$.exclusiveAssigneeId").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isString());
    }

    @Test
    @DisplayName("categoria sem rota e 409, e nenhum ticket e gravado")
    void categoriaSemRotaEh409() throws Exception {
        abrir(ana, "HARDWARE")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(ticketsDe(ana)).isZero();
    }

    @Test
    @DisplayName("titulo acima de 200 caracteres e 400 com o campo apontado")
    void tituloLongoEh400() throws Exception {
        mvc.perform(como(ana, post("/api/v1/tickets")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","description":"d","category":"ACCESS","priority":"LOW"}
                                """.formatted("t".repeat(201))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
    }

    @Test
    @DisplayName("o createdAt da resposta de criacao e o mesmo que o GET devolve depois")
    void createdAtEstavel() throws Exception {
        // O Postgres guarda microssegundos; sem truncar na auditoria, o POST devolvia o valor de
        // memoria, com mais casas, e o mesmo ticket tinha dois createdAt.
        String criado = abrir(ana, "ACCESS").andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(criado, "$.id");

        String lido = mvc.perform(como(ana, get("/api/v1/tickets/{id}", id.longValue())))
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(lido, "$.createdAt")).isEqualTo(JsonPath.read(criado, "$.createdAt"));
    }

    // --- listar ---------------------------------------------------------------------------

    @Test
    @DisplayName("a listagem traz so o que a pessoa ve, no formato de pagina da API")
    void listagemPaginada() throws Exception {
        Long daAna = ticketDaAna(TicketStatus.OPEN);
        Long doBruno = transacao.execute(s -> TicketBuilder.aTicket().requestedBy(bruno.getId())
                .assignedToTeam(equipe).persistIn(em).getId());

        mvc.perform(como(ana, get("/api/v1/tickets")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(daAna))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                // Nada da estrutura do Spring Data vaza para o contrato.
                .andExpect(jsonPath("$.pageable").doesNotExist());

        mvc.perform(como(igor, get("/api/v1/tickets")))
                .andExpect(jsonPath("$.content[?(@.id == %d || @.id == %d)]".formatted(daAna, doBruno)).isEmpty());
    }

    @Test
    @DisplayName("pagina negativa e tamanho fora de 1..100 sao 400")
    void limitesDaPagina() throws Exception {
        for (String consulta : new String[] {"page=-1", "size=0", "size=101"}) {
            mvc.perform(como(ana, get("/api/v1/tickets?" + consulta)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
    }

    // --- consultar ------------------------------------------------------------------------

    @Test
    @DisplayName("solicitante, membro da equipe e admin veem o ticket")
    void quemPodeVe() throws Exception {
        Long ticket = ticketDaAna(TicketStatus.OPEN);

        for (User quem : new User[] {ana, diego, admin}) {
            mvc.perform(como(quem, get("/api/v1/tickets/{id}", ticket))).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("outro solicitante e agente de outra equipe recebem 404, igual a um ticket inexistente")
    void quemNaoPodeNaoDistingueDeInexistente() throws Exception {
        Long ticket = ticketDaAna(TicketStatus.OPEN);
        String inexistente = corpoDe(como(igor, get("/api/v1/tickets/{id}", INEXISTENTE)));

        for (User quem : new User[] {bruno, igor}) {
            String invisivel = corpoDe(como(quem, get("/api/v1/tickets/{id}", ticket)));
            // Qualquer diferenca alem do caminho seria um oraculo: varrer ids contaria os
            // tickets das outras equipes.
            for (String campo : new String[] {"$.status", "$.title", "$.detail"}) {
                assertThat((Object) JsonPath.read(invisivel, campo)).isEqualTo(JsonPath.read(inexistente, campo));
            }
        }
    }

    @Test
    @DisplayName("tirado da equipe, o agente perde o ticket na hora, com o mesmo token")
    void saidaDaEquipeValeNaHora() throws Exception {
        // A equipe nao viaja no token (ADR 0003): a proxima requisicao ja consulta o banco.
        Long ticket = ticketDaAna(TicketStatus.OPEN);
        String tokenDoDiego = bearer(diego);
        mvc.perform(comToken(tokenDoDiego, get("/api/v1/tickets/{id}", ticket))).andExpect(status().isOk());

        jdbc.sql("DELETE FROM team_memberships WHERE user_id = :u").param("u", diego.getId()).update();

        mvc.perform(comToken(tokenDoDiego, get("/api/v1/tickets/{id}", ticket))).andExpect(status().isNotFound());
    }

    // --- transicionar ---------------------------------------------------------------------

    @Test
    @DisplayName("membro da equipe assume o ticket")
    void membroMove() throws Exception {
        Long ticket = ticketDaAna(TicketStatus.OPEN);

        mover(diego, ticket, "IN_PROGRESS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        assertThat(statusDe(ticket)).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("transicao fora do fluxo e 409, e o status nao muda")
    void foraDoFluxoEh409() throws Exception {
        Long ticket = ticketDaAna(TicketStatus.OPEN);

        mover(diego, ticket, "CLOSED").andExpect(status().isConflict());
        assertThat(statusDe(ticket)).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("solicitante nao conduz o atendimento: 403, e o status nao muda")
    void solicitanteNaoConduz() throws Exception {
        Long ticket = ticketDaAna(TicketStatus.OPEN);

        mover(ana, ticket, "IN_PROGRESS")
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(statusDe(ticket)).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("solicitante confirma o fechamento e depois reabre")
    void solicitanteFechaEReabre() throws Exception {
        Long ticket = ticketDaAna(TicketStatus.RESOLVED);

        mover(ana, ticket, "CLOSED").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        mover(ana, ticket, "REOPENED").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REOPENED"));
        assertThat(statusDe(ticket)).isEqualTo("REOPENED");
    }

    @Test
    @DisplayName("agente de outra equipe nao move o ticket: 404, e o status nao muda")
    void deOutraEquipeNaoMove() throws Exception {
        Long ticket = ticketDaAna(TicketStatus.OPEN);

        mover(igor, ticket, "IN_PROGRESS").andExpect(status().isNotFound());
        assertThat(statusDe(ticket)).isEqualTo("OPEN");
    }

    // --- conversar ------------------------------------------------------------------------

    @Test
    @DisplayName("o solicitante nunca recebe nota interna pelo endpoint; quem atende recebe tudo")
    void notaInternaNuncaChegaAoSolicitante() throws Exception {
        Long ticket = ticketDaAna(TicketStatus.IN_PROGRESS);
        comentar(diego, ticket, "Pode reiniciar?", false).andExpect(status().isCreated());
        comentar(diego, ticket, "Provavel DNS; nao falar ainda.", true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.internal").value(true));
        comentar(ana, ticket, "Reiniciei.", false).andExpect(status().isCreated());

        mvc.perform(como(ana, get("/api/v1/tickets/{id}/comments", ticket)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.internal == true)]").isEmpty())
                .andExpect(content().string(not(containsString("DNS"))));

        mvc.perform(como(diego, get("/api/v1/tickets/{id}/comments", ticket)))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[1].internal").value(true));
    }

    @Test
    @DisplayName("solicitante nao escreve nota interna: 403, e nada e gravado")
    void solicitanteNaoEscreveNotaInterna() throws Exception {
        Long ticket = ticketDaAna(TicketStatus.OPEN);

        comentar(ana, ticket, "Quero esconder isto", true).andExpect(status().isForbidden());
        assertThat(comentariosDe(ticket)).isZero();
    }

    @Test
    @DisplayName("comentario sem dizer se e interno e 400, e nada e gravado")
    void semFlagDeInternoEh400() throws Exception {
        // Com default false, a nota interna de quem esqueceu o campo iria para o cliente.
        Long ticket = ticketDaAna(TicketStatus.OPEN);

        mvc.perform(como(diego, post("/api/v1/tickets/{id}/comments", ticket))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Sem flag\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("internal"));
        assertThat(comentariosDe(ticket)).isZero();
    }

    @Test
    @DisplayName("agente de outra equipe nao le nem escreve na conversa: 404")
    void deOutraEquipeNaoConversa() throws Exception {
        Long ticket = ticketDaAna(TicketStatus.OPEN);
        transacao.executeWithoutResult(s -> em.persist(Comment.internalNote(ticket, diego.getId(), "segredo")));

        mvc.perform(como(igor, get("/api/v1/tickets/{id}/comments", ticket))).andExpect(status().isNotFound());
        comentar(igor, ticket, "intrometido", false).andExpect(status().isNotFound());
        assertThat(comentariosDe(ticket)).isEqualTo(1);
    }

    // --- apoio ----------------------------------------------------------------------------

    private Long ticketDaAna(TicketStatus status) {
        return transacao.execute(s -> TicketBuilder.aTicket().requestedBy(ana.getId()).assignedToTeam(equipe)
                .withCategory(TicketCategory.ACCESS).withStatus(status).persistIn(em).getId());
    }

    private ResultActions abrir(User quem, String categoria) throws Exception {
        return mvc.perform(como(quem, post("/api/v1/tickets")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Sem VPN","description":"Desde ontem.","category":"%s","priority":"HIGH"}
                        """.formatted(categoria)));
    }

    private ResultActions mover(User quem, Long ticket, String destino) throws Exception {
        return mvc.perform(como(quem, post("/api/v1/tickets/{id}/transitions", ticket))
                .contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"%s\"}".formatted(destino)));
    }

    private ResultActions comentar(User quem, Long ticket, String corpo, boolean interno) throws Exception {
        return mvc.perform(como(quem, post("/api/v1/tickets/{id}/comments", ticket))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"%s\",\"internal\":%s}".formatted(corpo, interno)));
    }

    private String corpoDe(MockHttpServletRequestBuilder pedido) throws Exception {
        return mvc.perform(pedido).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
    }

    private MockHttpServletRequestBuilder como(User quem, MockHttpServletRequestBuilder pedido) {
        return comToken(bearer(quem), pedido);
    }

    private static MockHttpServletRequestBuilder comToken(String token, MockHttpServletRequestBuilder pedido) {
        return pedido.header(HttpHeaders.AUTHORIZATION, token);
    }

    private String bearer(User pessoa) {
        return "Bearer " + tokens.valid(pessoa.getId(), pessoa.getRole());
    }

    private String statusDe(Long ticket) {
        return jdbc.sql("SELECT status FROM tickets WHERE id = :id").param("id", ticket).query(String.class).single();
    }

    private long ticketsDe(User pessoa) {
        return jdbc.sql("SELECT count(*) FROM tickets WHERE requester_id = :u")
                .param("u", pessoa.getId()).query(Long.class).single();
    }

    private long comentariosDe(Long ticket) {
        return jdbc.sql("SELECT count(*) FROM ticket_comments WHERE ticket_id = :t")
                .param("t", ticket).query(Long.class).single();
    }
}
