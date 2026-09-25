package com.ticketsystem.ticket.infra;

import com.ticketsystem.support.IntegrationTest;
import com.ticketsystem.support.TeamBuilder;
import com.ticketsystem.support.UserBuilder;
import com.ticketsystem.ticket.domain.TicketRepository;
import com.ticketsystem.ticket.domain.TicketRepositoryContractTest;
import jakarta.persistence.EntityManager;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * O adaptador JPA contra o contrato do repositorio de tickets, em Postgres real.
 *
 * <p>Nao ha caso de teste aqui: todos vem de {@link TicketRepositoryContractTest}. Esta
 * classe so responde os ganchos — que repositorio, quem existe e o que e uma transacao.
 */
@IntegrationTest
class JpaTicketRepositoryIT extends TicketRepositoryContractTest {

    @Autowired
    private TicketRepository repositorio;

    @Autowired
    private EntityManager em;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private JdbcClient jdbc;

    @AfterEach
    void limpar() {
        // Os tickets seguram pessoa e equipe por RESTRICT, entao saem primeiro.
        jdbc.sql("DELETE FROM tickets WHERE requester_id IN (SELECT id FROM users WHERE email LIKE :p)")
                .param("p", UserBuilder.PREFIXO + "%").update();
        jdbc.sql("DELETE FROM users WHERE email LIKE :p").param("p", UserBuilder.PREFIXO + "%").update();
        jdbc.sql("DELETE FROM teams WHERE name LIKE :p").param("p", TeamBuilder.PREFIXO + "%").update();
    }

    @Override
    protected TicketRepository repositorio() {
        return repositorio;
    }

    @Override
    protected Long umSolicitante() {
        return numaTransacao(() -> UserBuilder.aRequester().persistIn(em).getId());
    }

    @Override
    protected Long umaEquipe() {
        return numaTransacao(() -> TeamBuilder.aTeam().persistIn(em).getId());
    }

    @Override
    protected <T> T numaTransacao(Supplier<T> trabalho) {
        return transacao.execute(status -> trabalho.get());
    }
}
