package com.ticketsystem.ticket.domain;

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
 * </ul>
 *
 * <p>Nao ha listagem aqui ainda. Ela chega na Fase 5 junto com o filtro de visibilidade na
 * query — uma listagem sem o filtro nao pode existir nem por uma fase.
 */
public interface TicketRepository {

    Optional<Ticket> findById(Long id);

    Ticket save(Ticket ticket);
}
