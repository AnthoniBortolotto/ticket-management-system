package com.ticketsystem.team.domain;

import java.util.Optional;

/**
 * O que o dominio precisa perguntar sobre equipes.
 *
 * <p>Interface aqui, implementacao em {@code team.infra}: e o ponto de troca de
 * armazenamento, e o service nunca importa {@code jakarta.persistence} nem
 * {@code org.springframework.data}.
 */
public interface TeamRepository {

    Optional<Team> findById(Long id);

    /**
     * Ignora a caixa: "suporte" colide com "Suporte". E a mesma regra do indice
     * {@code teams_name_unique_idx}, conferida antes para a recusa sair como 409 e nao
     * como violacao de constraint.
     */
    boolean existsByName(String name);

    Team save(Team team);
}
