package com.ticketsystem.ticket.domain;

import java.util.Set;

/**
 * O que alguem pode enxergar, na forma que a consulta precisa: quem e, e as equipes dela.
 *
 * <p>E a ponte entre a regra de visibilidade (CLAUDE.md#visibilidade) e o SQL. O service a
 * monta a partir do papel global e das equipes lidas do banco na hora; o repositorio a traduz
 * em filtro <em>na query</em>. Nunca o contrario — carregar tudo e filtrar depois e
 * vazamento esperando um refactor.
 *
 * @param userId         quem pergunta
 * @param unrestricted   admin: ve tudo
 * @param handlesTickets pode atender ticket de equipe; {@code false} para o papel
 *                       {@code REQUESTER}, mesmo com vinculo de equipe esquecido
 * @param memberOf       equipes de que participa — ve os tickets em modo equipe delas
 * @param leads          equipes que lidera — ve tambem os que sairam delas para o exclusivo
 */
public record VisibilityScope(
        Long userId, boolean unrestricted, boolean handlesTickets, Set<Long> memberOf, Set<Long> leads) {

    public VisibilityScope {
        memberOf = Set.copyOf(memberOf);
        leads = Set.copyOf(leads);
    }

    public static VisibilityScope everything(Long userId) {
        return new VisibilityScope(userId, true, true, Set.of(), Set.of());
    }

    /** So os proprios tickets. As equipes nem entram: o papel basta para responder. */
    public static VisibilityScope ownTicketsOnly(Long userId) {
        return new VisibilityScope(userId, false, false, Set.of(), Set.of());
    }

    public static VisibilityScope agent(Long userId, Set<Long> memberOf, Set<Long> leads) {
        return new VisibilityScope(userId, false, true, memberOf, leads);
    }
}
