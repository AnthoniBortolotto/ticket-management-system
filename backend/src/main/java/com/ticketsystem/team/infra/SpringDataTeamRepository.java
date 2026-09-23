package com.ticketsystem.team.infra;

import com.ticketsystem.team.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Detalhe de implementacao: o service nao a enxerga.
 *
 * <p>{@code IgnoreCase} vira {@code upper(name) = upper(?)}, que nao usa o indice
 * funcional sobre {@code lower(name)}. Aceitavel: a consulta so roda ao criar equipe, e a
 * tabela tem poucas linhas. Quem garante a unicidade de verdade continua sendo o indice.
 */
interface SpringDataTeamRepository extends JpaRepository<Team, Long> {

    boolean existsByNameIgnoreCase(String name);
}
