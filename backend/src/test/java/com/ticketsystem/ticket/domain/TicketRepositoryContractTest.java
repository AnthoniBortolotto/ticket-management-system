package com.ticketsystem.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketsystem.ticket.TicketStatus;
import java.util.List;
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

    @Test
    @DisplayName("a listagem vem por prioridade, URGENT primeiro, e entre iguais do mais antigo")
    void listagemOrdenada() {
        Long solicitante = umSolicitante();
        Long equipe = umaEquipe();
        Long baixa = gravar(solicitante, equipe, TicketPriority.LOW);
        Long altaAntiga = gravar(solicitante, equipe, TicketPriority.HIGH);
        Long urgente = gravar(solicitante, equipe, TicketPriority.URGENT);
        Long altaNova = gravar(solicitante, equipe, TicketPriority.HIGH);

        // O escopo do proprio solicitante isola este teste do que mais houver no armazenamento.
        TicketPage pagina = numaTransacao(() -> repositorio().findVisible(VisibilityScope.ownTicketsOnly(solicitante), 0, 10));

        assertThat(pagina.content()).extracting(Ticket::getId).containsExactly(urgente, altaAntiga, altaNova, baixa);
    }

    @Test
    @DisplayName("a pagina traz o recorte pedido e o total de tudo o que o escopo enxerga")
    void paginacao() {
        Long solicitante = umSolicitante();
        Long equipe = umaEquipe();
        Long primeiro = gravar(solicitante, equipe, TicketPriority.MEDIUM);
        Long segundo = gravar(solicitante, equipe, TicketPriority.MEDIUM);
        Long terceiro = gravar(solicitante, equipe, TicketPriority.MEDIUM);
        VisibilityScope escopo = VisibilityScope.ownTicketsOnly(solicitante);

        TicketPage primeira = numaTransacao(() -> repositorio().findVisible(escopo, 0, 2));
        TicketPage segunda = numaTransacao(() -> repositorio().findVisible(escopo, 1, 2));

        assertThat(primeira.content()).extracting(Ticket::getId).containsExactly(primeiro, segundo);
        assertThat(segunda.content()).extracting(Ticket::getId).containsExactly(terceiro);
        assertThat(primeira.totalElements()).isEqualTo(3);
        assertThat(primeira.totalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("a atribuicao gravada e a que volta, nos quatro campos")
    void atribuicaoGravadaVolta() {
        Long solicitante = umSolicitante();
        Long equipe = umaEquipe();
        Long gravado = gravar(solicitante, equipe, TicketPriority.MEDIUM);

        numaTransacao(() -> {
            Ticket ticket = repositorio().findById(gravado).orElseThrow();
            ticket.makeExclusive(solicitante);
            return repositorio().save(ticket);
        });

        Ticket lido = numaTransacao(() -> repositorio().findById(gravado).orElseThrow());
        assertThat(lido.getExclusiveAssigneeId()).isEqualTo(solicitante);
        assertThat(lido.getOriginTeamId()).isEqualTo(equipe);
        assertThat(lido.getAssignedTeamId()).isNull();
    }

    @Test
    @DisplayName("os tickets de uma equipe com um responsavel atual — nem de outra equipe, nem de outro responsavel")
    void comResponsavelAtualNaEquipe() {
        Long pessoa = umSolicitante();
        Long outraPessoa = umSolicitante();
        Long equipe = umaEquipe();
        Long outraEquipe = umaEquipe();
        Long dela = gravarComResponsavel(pessoa, equipe, pessoa);
        gravarComResponsavel(pessoa, equipe, outraPessoa);
        gravarComResponsavel(pessoa, outraEquipe, pessoa);

        List<Ticket> encontrados = numaTransacao(() -> repositorio().findInTeamWithCurrentAssignee(equipe, pessoa));

        assertThat(encontrados).extracting(Ticket::getId).containsExactly(dela);
    }

    private Long gravar(Long solicitante, Long equipe, TicketPriority prioridade) {
        return numaTransacao(() -> repositorio().save(Ticket.open(solicitante, equipe, "Titulo", "Descricao",
                TicketCategory.OTHER, prioridade)).getId());
    }

    private Long gravarComResponsavel(Long solicitante, Long equipe, Long responsavel) {
        return numaTransacao(() -> {
            Ticket ticket = Ticket.open(solicitante, equipe, "Titulo", "Descricao", TicketCategory.OTHER, TicketPriority.LOW);
            ticket.assignCurrent(responsavel);
            return repositorio().save(ticket).getId();
        });
    }

    private static Ticket novoTicket(Long solicitante, Long equipe) {
        return Ticket.open(solicitante, equipe, "Sem acesso a VPN", "Desde a troca de senha.",
                TicketCategory.ACCESS, TicketPriority.HIGH);
    }
}
