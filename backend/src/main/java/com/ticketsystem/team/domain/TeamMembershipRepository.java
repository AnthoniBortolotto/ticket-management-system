package com.ticketsystem.team.domain;

import java.util.List;
import java.util.Optional;

/**
 * Consulta e escrita de vinculos entre usuario e equipe.
 *
 * <p>A ordem dos parametros e sempre {@code (teamId, userId)} — a mesma da URL
 * {@code /teams/{teamId}/members/{userId}}. Sao dois {@code Long} lado a lado, e uma troca
 * de ordem compila, passa em qualquer teste que use o mesmo numero para os dois e responde
 * sobre a pessoa errada. Uma ordem so, no modulo inteiro, e o que torna esse erro visivel
 * na leitura.
 */
public interface TeamMembershipRepository {

    /** O vinculo desta pessoa com esta equipe, se existir. Uma linha, pelo UNIQUE do par. */
    Optional<TeamMembership> find(Long teamId, Long userId);

    /** Os vinculos da equipe, na ordem em que foram criados. */
    List<TeamMembership> findByTeam(Long teamId);

    TeamMembership save(TeamMembership membership);

    void delete(TeamMembership membership);
}
