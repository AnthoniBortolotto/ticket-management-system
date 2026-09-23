package com.ticketsystem.team.infra;

import com.ticketsystem.team.domain.TeamMembership;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Detalhe de implementacao: o service nao a enxerga.
 *
 * <p>A busca pelo par usa o UNIQUE {@code (user_id, team_id)} da V2 como indice; a
 * listagem por equipe usa {@code team_memberships_team_idx}. As duas perguntas que a
 * visibilidade faz — "de que equipes esta pessoa participa?" e "quem esta nesta equipe?" —
 * ja tem indice desde a Fase 1.
 */
interface SpringDataTeamMembershipRepository extends JpaRepository<TeamMembership, Long> {

    Optional<TeamMembership> findByTeamIdAndUserId(Long teamId, Long userId);

    List<TeamMembership> findByTeamIdOrderByIdAsc(Long teamId);
}
