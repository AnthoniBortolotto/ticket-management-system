package com.ticketsystem.ticket.domain;

import com.ticketsystem.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Qual equipe recebe os tickets abertos numa categoria.
 *
 * <p>E dado, no banco, e nao regra em Java: mudar para onde vao os chamados de acesso e
 * uma chamada de API de admin, nao um deploy. Redirecionar a rota vale para os proximos
 * tickets; os ja abertos continuam onde estao.
 */
@Entity
@Table(name = "ticket_routes")
public class TicketRoute extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TicketCategory category;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    /** Exigido pelo JPA. Nao use no codigo da aplicacao. */
    protected TicketRoute() {
    }

    public TicketRoute(TicketCategory category, Long teamId) {
        if (category == null) {
            throw new IllegalArgumentException("rota precisa de categoria");
        }
        this.category = category;
        redirectTo(teamId);
    }

    public void redirectTo(Long teamId) {
        if (teamId == null) {
            throw new IllegalArgumentException("rota precisa de equipe");
        }
        this.teamId = teamId;
    }

    public TicketCategory getCategory() {
        return category;
    }

    public Long getTeamId() {
        return teamId;
    }

    @Override
    public String toString() {
        return "TicketRoute[category=%s, teamId=%s]".formatted(category, teamId);
    }
}
