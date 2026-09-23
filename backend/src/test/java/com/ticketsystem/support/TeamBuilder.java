package com.ticketsystem.support;

import com.ticketsystem.team.domain.Team;
import com.ticketsystem.team.domain.TeamMembership;
import com.ticketsystem.team.domain.TeamRole;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Monta equipes de teste, com os vinculos, em uma linha.
 *
 * <p>O caso que justifica a classe e alguem em duas equipes — o vinculo e
 * muitos-para-muitos, e e esse o cenario que a Fase 5 exercita na visibilidade:
 *
 * <pre>{@code
 * TeamBuilder.aTeam().withMember(marina.getId()).persistIn(em);
 * TeamBuilder.aTeam().withLead(marina.getId()).persistIn(em);
 * }</pre>
 *
 * <p>Recebe o <em>id</em> do usuario, e nao a entidade {@code User}: e o que o proprio
 * {@code TeamMembership} guarda. O Modulith nao analisa codigo de teste, entao e em
 * {@code support} que a fronteira entre {@code team} e {@code user} se dissolveria sem
 * ninguem reclamar — este builder nao a cruza.
 */
public final class TeamBuilder {

    /** Prefixo de limpeza: os testes apagam por ele no {@code @AfterEach}. */
    public static final String PREFIXO = "builder-team-";

    private static final AtomicLong SEQUENCIA = new AtomicLong();

    private String name;
    private final Map<Long, TeamRole> vinculos = new LinkedHashMap<>();

    private TeamBuilder() {
        // Unico por padrao: o nome tem UNIQUE sem distinguir caixa, e um nome fixo
        // estouraria na segunda equipe do mesmo teste.
        this.name = PREFIXO + SEQUENCIA.incrementAndGet();
    }

    public static TeamBuilder aTeam() {
        return new TeamBuilder();
    }

    public TeamBuilder named(String name) {
        this.name = name;
        return this;
    }

    public TeamBuilder withMember(Long userId) {
        vinculos.put(userId, TeamRole.MEMBER);
        return this;
    }

    public TeamBuilder withLead(Long userId) {
        vinculos.put(userId, TeamRole.LEAD);
        return this;
    }

    /**
     * Uma equipe em memoria, com id sintetico — sem id, {@code BaseEntity.equals} nunca
     * considera duas instancias iguais. Os vinculos ficam de fora: em unitario eles sao
     * respostas de um repositorio mockado, e quem os monta e {@link #membership}.
     */
    public Team build() {
        Team equipe = new Team(name, null);
        ReflectionTestUtils.setField(equipe, "id", SEQUENCIA.incrementAndGet());
        return equipe;
    }

    /** Grava a equipe e os vinculos, e devolve a equipe gerenciada. Exige transacao aberta. */
    public Team persistIn(EntityManager em) {
        Team equipe = new Team(name, null);
        em.persist(equipe);
        em.flush();
        vinculos.forEach((userId, papel) -> em.persist(membership(equipe.getId(), userId, papel)));
        em.flush();
        return equipe;
    }

    /**
     * Um vinculo avulso. Existe porque a entidade so nasce {@code MEMBER} — de proposito,
     * ver {@code TeamMembership.member} — e lider de teste seria sempre duas linhas.
     */
    public static TeamMembership membership(Long teamId, Long userId, TeamRole papel) {
        TeamMembership vinculo = TeamMembership.member(teamId, userId);
        vinculo.changeRole(papel);
        return vinculo;
    }
}
