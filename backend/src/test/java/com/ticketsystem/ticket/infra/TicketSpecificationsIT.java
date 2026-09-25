package com.ticketsystem.ticket.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketsystem.auth.CurrentUser;
import com.ticketsystem.support.IntegrationTest;
import com.ticketsystem.support.TeamBuilder;
import com.ticketsystem.support.TicketBuilder;
import com.ticketsystem.support.UserBuilder;
import com.ticketsystem.ticket.domain.Ticket;
import com.ticketsystem.ticket.domain.TicketRepository;
import com.ticketsystem.ticket.service.TicketAccessPolicy;
import com.ticketsystem.ticket.service.TicketService;
import com.ticketsystem.user.domain.User;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A regra de visibilidade da listagem, contra Postgres real — <strong>escrito antes da
 * implementacao</strong>, como manda o CLAUDE.md: a regra so existe quando o banco executa a
 * query, e com repositorio mockado o teste provaria so que o mock foi chamado.
 *
 * <p>Um caso por linha da tabela de visibilidade, cada um afirmando o conjunto
 * <em>exato</em> — o que a pessoa ve e, por consequencia, tudo o que ela nao ve.
 *
 * <p>O elenco:
 * <pre>
 * Suporte:  Carla (LEAD), Diego, Marina           Infra:  Helena (LEAD), Igor, Marina
 * T1  da Ana,    modo equipe, Suporte
 * T2  do Bruno,  modo equipe, Infra
 * T3  da Ana,    exclusivo do Diego, origem Suporte
 * T4  do Bruno,  exclusivo do Igor,  origem Infra
 * </pre>
 * Mais um agente sem equipe e um solicitante com um vinculo de equipe esquecido.
 *
 * <p>O ultimo teste confere, pessoa por pessoa e ticket por ticket, que esta listagem e o
 * {@link TicketAccessPolicy} — a mesma regra, em Java, para um ticket ja carregado — dao a
 * mesma resposta. Se as duas divergirem, alguem ve na lista o que nao abre, ou abre pela URL o
 * que a lista esconde.
 */
@IntegrationTest
class TicketSpecificationsIT {

    @Autowired
    private TicketService service;

    @Autowired
    private TicketAccessPolicy acesso;

    @Autowired
    private TicketRepository repositorio;

    @Autowired
    private EntityManager em;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private JdbcClient jdbc;

    private User admin, ana, bruno, carla, diego, marina, helena, igor, semEquipe, solicitanteComVinculo;
    private Long t1, t2, t3, t4;

    @BeforeEach
    void montarElenco() {
        transacao.executeWithoutResult(s -> {
            admin = UserBuilder.anAdmin().persistIn(em);
            ana = UserBuilder.aRequester().persistIn(em);
            bruno = UserBuilder.aRequester().persistIn(em);
            carla = UserBuilder.anAgent().persistIn(em);
            diego = UserBuilder.anAgent().persistIn(em);
            marina = UserBuilder.anAgent().persistIn(em);
            helena = UserBuilder.anAgent().persistIn(em);
            igor = UserBuilder.anAgent().persistIn(em);
            semEquipe = UserBuilder.anAgent().persistIn(em);
            solicitanteComVinculo = UserBuilder.aRequester().persistIn(em);

            // O builder grava direto, sem passar pela regra da Fase 3 que impede solicitante em
            // equipe: e o vinculo esquecido de um agente rebaixado.
            Long suporte = TeamBuilder.aTeam().withLead(carla.getId()).withMember(diego.getId())
                    .withMember(marina.getId()).withMember(solicitanteComVinculo.getId()).persistIn(em).getId();
            Long infra = TeamBuilder.aTeam().withLead(helena.getId()).withMember(igor.getId())
                    .withMember(marina.getId()).persistIn(em).getId();

            t1 = TicketBuilder.aTicket().requestedBy(ana.getId()).assignedToTeam(suporte).persistIn(em).getId();
            t2 = TicketBuilder.aTicket().requestedBy(bruno.getId()).assignedToTeam(infra).persistIn(em).getId();
            t3 = TicketBuilder.aTicket().requestedBy(ana.getId()).inExclusiveMode(diego.getId(), suporte)
                    .persistIn(em).getId();
            t4 = TicketBuilder.aTicket().requestedBy(bruno.getId()).inExclusiveMode(igor.getId(), infra)
                    .persistIn(em).getId();
        });
    }

    @AfterEach
    void limpar() {
        jdbc.sql("DELETE FROM tickets WHERE requester_id IN (SELECT id FROM users WHERE email LIKE :p)")
                .param("p", UserBuilder.PREFIXO + "%").update();
        jdbc.sql("DELETE FROM users WHERE email LIKE :p").param("p", UserBuilder.PREFIXO + "%").update();
        jdbc.sql("DELETE FROM teams WHERE name LIKE :p").param("p", TeamBuilder.PREFIXO + "%").update();
    }

    @Test
    @DisplayName("solicitante ve os proprios tickets, em qualquer modo — e nenhum de outro solicitante")
    void solicitante() {
        assertThat(visiveis(ana)).containsExactlyInAnyOrder(t1, t3);
        assertThat(visiveis(bruno)).containsExactlyInAnyOrder(t2, t4);
    }

    @Test
    @DisplayName("membro ve os tickets em modo equipe das equipes dele — nao os que sairam delas")
    void membro() {
        // Marina esta nas duas equipes: e o caso que prova que o vinculo e muitos-para-muitos.
        // T3 e T4 sairam das equipes dela para o modo exclusivo, e a equipe perde o acesso.
        assertThat(visiveis(marina)).containsExactlyInAnyOrder(t1, t2);
    }

    @Test
    @DisplayName("lider ve os da equipe e os que sairam dela para o exclusivo — nao os de outra equipe")
    void lider() {
        assertThat(visiveis(carla)).containsExactlyInAnyOrder(t1, t3);
        assertThat(visiveis(helena)).containsExactlyInAnyOrder(t2, t4);
    }

    @Test
    @DisplayName("responsavel exclusivo ve o ticket atribuido a ele — nao o exclusivo de outro")
    void responsavelExclusivo() {
        // Diego tambem e membro do Suporte, por isso ve T1.
        assertThat(visiveis(diego)).containsExactlyInAnyOrder(t1, t3);
        assertThat(visiveis(igor)).containsExactlyInAnyOrder(t2, t4);
    }

    @Test
    @DisplayName("agente sem equipe nao ve nada")
    void agenteSemEquipe() {
        assertThat(visiveis(semEquipe)).isEmpty();
    }

    @Test
    @DisplayName("solicitante com vinculo de equipe esquecido nao ve os tickets da equipe")
    void solicitanteComVinculoEsquecido() {
        assertThat(visiveis(solicitanteComVinculo)).isEmpty();
    }

    @Test
    @DisplayName("admin ve tudo")
    void admin() {
        assertThat(visiveis(admin)).contains(t1, t2, t3, t4);
    }

    @Test
    @DisplayName("a listagem e a regra do ticket carregado concordam, pessoa por pessoa, ticket por ticket")
    void listagemERegraConcordam() {
        Map<Long, Ticket> elenco = transacao.execute(s -> List.of(t1, t2, t3, t4).stream()
                .collect(Collectors.toMap(id -> id, id -> repositorio.findById(id).orElseThrow())));

        for (User pessoa : List.of(admin, ana, bruno, carla, diego, marina, helena, igor, semEquipe, solicitanteComVinculo)) {
            Set<Long> naLista = Set.copyOf(visiveis(pessoa));
            for (Ticket ticket : elenco.values()) {
                assertThat(naLista.contains(ticket.getId()))
                        .as("%s e o ticket %d", pessoa.getEmail(), ticket.getId())
                        .isEqualTo(acesso.canView(ator(pessoa), ticket));
            }
        }
    }

    private List<Long> visiveis(User pessoa) {
        return service.list(ator(pessoa), 0, 100).content().stream().map(Ticket::getId).toList();
    }

    private static CurrentUser ator(User pessoa) {
        return new CurrentUser(pessoa.getId(), pessoa.getRole());
    }
}
