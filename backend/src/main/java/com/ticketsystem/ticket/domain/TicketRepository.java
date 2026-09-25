package com.ticketsystem.ticket.domain;

import java.util.List;
import java.util.Optional;

/**
 * O que o dominio precisa do armazenamento de tickets. <strong>E o ponto de troca</strong>
 * (CLAUDE.md#trocar-uma-implementacao-sem-quebrar-nada): o service conhece so esta
 * interface, e um adaptador novo — outro banco, um servico externo — implementa estes mesmos
 * metodos e roda o {@code TicketRepositoryContractTest} sem reescrever teste nenhum.
 *
 * <p>Contrato que a assinatura nao expressa, e que o teste de contrato cobra de todo
 * adaptador:
 * <ul>
 *   <li>{@link #save} de um ticket carregado antes de outra gravacao do mesmo ticket e
 *       recusado com {@code OptimisticLockingFailureException}. Sem isso, duas transicoes
 *       simultaneas passariam as duas.</li>
 *   <li>{@link #findVisible} filtra <strong>no armazenamento</strong>, nunca em memoria, e
 *       ordena por prioridade ({@code URGENT} primeiro), depois do mais antigo para o mais
 *       novo, e por fim pelo id — para a paginacao ser estavel entre chamadas.</li>
 * </ul>
 */
public interface TicketRepository {

    Optional<Ticket> findById(Long id);

    Ticket save(Ticket ticket);

    /** Uma pagina dos tickets que o escopo enxerga. {@code page} comeca em zero. */
    TicketPage findVisible(VisibilityScope scope, int page, int size);

    /**
     * Os tickets em modo equipe de {@code teamId} com {@code userId} como responsavel atual.
     *
     * <p>Consulta de sistema, sem filtro de visibilidade: serve a limpeza que acontece quando
     * alguem sai da equipe, e nunca chega a uma resposta HTTP.
     */
    List<Ticket> findInTeamWithCurrentAssignee(Long teamId, Long userId);
}
