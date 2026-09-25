package com.ticketsystem.team;

import java.util.Set;

/**
 * As equipes de uma pessoa, lidas do banco na hora — nunca do token.
 *
 * <p>E o que o filtro da listagem de tickets precisa para decidir, na query, o que alguem
 * enxerga: os tickets em modo equipe de {@code memberOf} e os que sairam para o modo
 * exclusivo a partir de {@code leads}. Todo lider tambem e membro, entao {@code leads} esta
 * sempre contido em {@code memberOf}.
 */
public record UserTeams(Set<Long> memberOf, Set<Long> leads) {

    public UserTeams {
        memberOf = Set.copyOf(memberOf);
        leads = Set.copyOf(leads);
    }
}
