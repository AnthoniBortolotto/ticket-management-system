package com.ticketsystem.team.service;

import com.ticketsystem.common.error.DomainException;
import com.ticketsystem.common.error.ProblemKind;

/**
 * Quem pede enxerga a equipe, mas nao pode fazer isto com ela.
 *
 * <p>Uma classe, duas mensagens: a resposta diz <em>quem</em> pode, para quem integra
 * saber a quem recorrer, sem descrever o estado de ninguem.
 */
public class TeamActionForbiddenException extends DomainException {

    private TeamActionForbiddenException(String detail) {
        super(ProblemKind.FORBIDDEN, "Operacao nao permitida na equipe", detail);
    }

    /** Criar equipe e decidir quem lidera. */
    public static TeamActionForbiddenException requiresAdmin() {
        return new TeamActionForbiddenException("Apenas administradores criam equipes e decidem quem as lidera.");
    }

    /** Adicionar e remover membro comum. */
    public static TeamActionForbiddenException requiresAdminOrLead() {
        return new TeamActionForbiddenException("Apenas administradores e lideres desta equipe gerenciam os membros dela.");
    }
}
