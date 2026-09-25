package com.ticketsystem.ticket.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ticketsystem.support.IntegrationTest;
import com.ticketsystem.support.TeamBuilder;
import com.ticketsystem.support.TicketBuilder;
import com.ticketsystem.support.UserBuilder;
import com.ticketsystem.ticket.domain.Comment;
import com.ticketsystem.ticket.domain.CommentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A conversa de um ticket contra Postgres real.
 *
 * <p>O caso que importa e o primeiro: a consulta publica exclui a nota interna no SQL.
 * Com repositorio mockado, o teste provaria so que o mock foi chamado.
 */
@IntegrationTest
class CommentRepositoryIT {

    @Autowired
    private CommentRepository comentarios;

    @Autowired
    private EntityManager em;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private JdbcClient jdbc;

    private Long ticket;
    private Long outroTicket;
    private Long autor;

    @BeforeEach
    void montar() {
        transacao.executeWithoutResult(s -> {
            autor = UserBuilder.anAgent().persistIn(em).getId();
            Long solicitante = UserBuilder.aRequester().persistIn(em).getId();
            Long equipe = TeamBuilder.aTeam().withMember(autor).persistIn(em).getId();
            ticket = TicketBuilder.aTicket().requestedBy(solicitante).assignedToTeam(equipe).persistIn(em).getId();
            outroTicket = TicketBuilder.aTicket().requestedBy(solicitante).assignedToTeam(equipe).persistIn(em).getId();
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
    @DisplayName("a consulta publica nao traz nota interna")
    void consultaPublicaSemNotaInterna() {
        gravar(Comment.publicReply(ticket, autor, "Pode reiniciar?"));
        gravar(Comment.internalNote(ticket, autor, "Provavel DNS; nao falar ainda."));
        gravar(Comment.publicReply(ticket, autor, "Reiniciei e voltou."));

        assertThat(comentarios.findPublicByTicket(ticket))
                .extracting(Comment::getBody)
                .containsExactly("Pode reiniciar?", "Reiniciei e voltou.");
    }

    @Test
    @DisplayName("a conversa completa traz tudo, na ordem em que foi escrito, so deste ticket")
    void conversaCompletaEmOrdem() {
        gravar(Comment.publicReply(ticket, autor, "primeiro"));
        gravar(Comment.internalNote(outroTicket, autor, "de outro ticket"));
        gravar(Comment.internalNote(ticket, autor, "segundo"));

        assertThat(comentarios.findByTicket(ticket))
                .extracting(Comment::getBody, Comment::isInternal)
                .containsExactly(
                        tuple("primeiro", false),
                        tuple("segundo", true));
    }

    private void gravar(Comment comentario) {
        transacao.executeWithoutResult(s -> comentarios.save(comentario));
    }
}
