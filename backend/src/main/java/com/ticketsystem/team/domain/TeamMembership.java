package com.ticketsystem.team.domain;

import com.ticketsystem.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * O vinculo de um usuario com uma equipe, e o papel dele ali.
 *
 * <p>Uma pessoa pode ter varios — um por equipe. O UNIQUE da V2 e sobre o par
 * {@code (user_id, team_id)}, nunca sobre o usuario sozinho.
 *
 * <p>{@code userId} e um {@code Long} puro, sem {@code @ManyToOne User}: {@code team} nao
 * pode referenciar classe interna do modulo {@code user}, e o {@code ModularityTest}
 * quebraria o build. {@code teamId} segue o mesmo formato por simetria — o vinculo e
 * consultado pelo par de ids, nunca navegando a partir da equipe.
 *
 * <p>As duas pontas nao mudam depois de gravadas ({@code updatable = false}): mover alguem
 * de equipe e remover um vinculo e criar outro, para que a troca apareca como duas
 * operacoes e nao como um UPDATE silencioso.
 */
@Entity
@Table(name = "team_memberships")
public class TeamMembership extends BaseEntity {

    @Column(name = "team_id", nullable = false, updatable = false)
    private Long teamId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TeamRole role;

    /** Exigido pelo JPA. Nao use no codigo da aplicacao. */
    protected TeamMembership() {
    }

    private TeamMembership(Long teamId, Long userId, TeamRole role) {
        this.teamId = teamId;
        this.userId = userId;
        this.role = role;
    }

    /**
     * Alguem entrando na equipe — sempre como {@link TeamRole#MEMBER}.
     *
     * <p>Nao ha fabrica que ja nasca {@code LEAD}: lideranca amplia visibilidade e e
     * concedida por {@link #changeRole}, que o service so permite a admin.
     */
    public static TeamMembership member(Long teamId, Long userId) {
        if (teamId == null || userId == null) {
            throw new IllegalArgumentException("vinculo precisa de equipe e de usuario");
        }
        return new TeamMembership(teamId, userId, TeamRole.MEMBER);
    }

    public void changeRole(TeamRole role) {
        if (role == null) {
            throw new IllegalArgumentException("papel na equipe e obrigatorio");
        }
        this.role = role;
    }

    public boolean isLead() {
        return role == TeamRole.LEAD;
    }

    public Long getTeamId() {
        return teamId;
    }

    public Long getUserId() {
        return userId;
    }

    public TeamRole getRole() {
        return role;
    }

    @Override
    public String toString() {
        return "TeamMembership[teamId=%s, userId=%s, role=%s]".formatted(teamId, userId, role);
    }
}
