package com.ticketsystem.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketsystem.ticket.TicketStatus;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * O contrato de {@link TicketRepository}, escrito contra a <strong>interface</strong>.
 *
 * <p>E o que torna a troca de armazenamento possivel sem reescrever teste
 * (CLAUDE.md#trocar-uma-implementacao-sem-quebrar-nada): um adaptador novo estende esta
 * classe, responde os quatro ganchos abaixo e roda exatamente estes casos. Nenhum deles sabe
 * que existe JPA.
 *
 * <p>Abstrata, entao o JUnit so a executa pelas subclasses. Hoje ha uma:
 * {@code JpaTicketRepositoryIT}, contra Postgres real.
 */
public abstract class TicketRepositoryContractTest {

    /** O adaptador sob teste. */
    protected abstract TicketRepository repositorio();

    /** Id de uma pessoa que existe no armazenamento, para ser a solicitante. */
    protected abstract Long umSolicitante();

    /** Id de uma equipe que existe no armazenamento. */
    protected abstract Long umaEquipe();

    /**
     * Roda o trabalho numa unidade de gravacao propria e devolve o resultado desligado dela.
     * Para JPA, uma transacao; para um servico externo, possivelmente nada.
     */
    protected abstract <T> T numaTransacao(Supplier<T> trabalho);

    @Test
    @DisplayName("o ticket gravado volta pelo id, com todos os campos")
    void gravadoVoltaPeloId() {
        Long solicitante = umSolicitante();
        Long equipe = umaEquipe();
        Ticket gravado = numaTransacao(() -> repositorio().save(novoTicket(solicitante, equipe)));

        Ticket lido = numaTransacao(() -> repositorio().findById(gravado.getId()).orElseThrow());

        assertThat(lido.getId()).isEqualTo(gravado.getId());
        assertThat(lido.getTitle()).isEqualTo("Sem acesso a VPN");
        assertThat(lido.getDescription()).isEqualTo("Desde a troca de senha.");
        assertThat(lido.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(lido.getCategory()).isEqualTo(TicketCategory.ACCESS);
        assertThat(lido.getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(lido.getRequesterId()).isEqualTo(solicitante);
        assertThat(lido.getAssignedTeamId()).isEqualTo(equipe);
        assertThat(lido.isInTeamMode()).isTrue();
        assertThat(lido.getCreatedAt()).isNotNull();
        assertThat(lido.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("id que nao existe volta vazio, e nao nulo nem excecao")
    void idInexistenteVoltaVazio() {
        assertThat(numaTransacao(() -> repositorio().findById(Long.MAX_VALUE))).isEmpty();
    }

    @Test
    @DisplayName("a transicao gravada e a que volta")
    void transicaoGravadaVolta() {
        Ticket gravado = numaTransacao(() -> repositorio().save(novoTicket(umSolicitante(), umaEquipe())));

        numaTransacao(() -> {
            Ticket ticket = repositorio().findById(gravado.getId()).orElseThrow();
            ticket.transitionTo(TicketStatus.IN_PROGRESS);
            return repositorio().save(ticket);
        });

        assertThat(numaTransacao(() -> repositorio().findById(gravado.getId()).orElseThrow()).getStatus())
                .isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("gravar uma copia desatualizada e recusado, e a gravacao anterior sobrevive")
    void copiaDesatualizadaEhRecusada() {
        // Duas pessoas abrem o mesmo ticket; a primeira resolve, a segunda — olhando o status
        // antigo — o coloca em espera. Sem a recusa, a segunda apagaria a resolucao em silencio,
        // e dois eventos contariam uma historia que so aconteceu pela metade.
        Ticket gravado = numaTransacao(() -> repositorio().save(novoTicket(umSolicitante(), umaEquipe())));
        numaTransacao(() -> {
            Ticket ticket = repositorio().findById(gravado.getId()).orElseThrow();
            ticket.transitionTo(TicketStatus.IN_PROGRESS);
            return repositorio().save(ticket);
        });

        Ticket daPrimeira = numaTransacao(() -> repositorio().findById(gravado.getId()).orElseThrow());
        Ticket daSegunda = numaTransacao(() -> repositorio().findById(gravado.getId()).orElseThrow());

        daPrimeira.transitionTo(TicketStatus.RESOLVED);
        numaTransacao(() -> repositorio().save(daPrimeira));

        daSegunda.transitionTo(TicketStatus.WAITING_CUSTOMER);
        assertThatThrownBy(() -> numaTransacao(() -> repositorio().save(daSegunda)))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(numaTransacao(() -> repositorio().findById(gravado.getId()).orElseThrow()).getStatus())
                .isEqualTo(TicketStatus.RESOLVED);
    }

    private static Ticket novoTicket(Long solicitante, Long equipe) {
        return Ticket.open(solicitante, equipe, "Sem acesso a VPN", "Desde a troca de senha.",
                TicketCategory.ACCESS, TicketPriority.HIGH);
    }
}
