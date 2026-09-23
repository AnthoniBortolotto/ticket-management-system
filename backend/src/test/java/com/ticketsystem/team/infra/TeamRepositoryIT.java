package com.ticketsystem.team.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ticketsystem.support.IntegrationTest;
import com.ticketsystem.support.TeamBuilder;
import com.ticketsystem.support.UserBuilder;
import com.ticketsystem.team.domain.Team;
import com.ticketsystem.team.domain.TeamMembership;
import com.ticketsystem.team.domain.TeamMembershipRepository;
import com.ticketsystem.team.domain.TeamRepository;
import com.ticketsystem.team.domain.TeamRole;
import jakarta.persistence.EntityManager;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Os repositorios de equipe contra Postgres real.
 *
 * <p>Tambem prova que {@link Team} e {@link TeamMembership} casam com a V2 — o
 * {@code ddl-auto: validate} confere o tipo das colunas, mas so um INSERT de verdade prova
 * que as larguras e os CHECKs aceitam o que as entidades gravam.
 */
@IntegrationTest
class TeamRepositoryIT {

    @Autowired
    private TeamRepository equipes;

    @Autowired
    private TeamMembershipRepository vinculos;

    @Autowired
    private EntityManager em;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private JdbcClient jdbc;

    @AfterEach
    void limpar() {
        // A cascata da V2 leva os vinculos junto com a equipe e com o usuario.
        jdbc.sql("DELETE FROM teams WHERE name LIKE :p").param("p", TeamBuilder.PREFIXO + "%").update();
        jdbc.sql("DELETE FROM users WHERE email LIKE :p").param("p", UserBuilder.PREFIXO + "%").update();
    }

    @Test
    @DisplayName("alguem em duas equipes tem um vinculo independente em cada")
    void pessoaEmDuasEquipes() {
        // O caso que a Fase 5 exercita na visibilidade. Se o vinculo fosse uma coluna em
        // users, ou se o UNIQUE fosse so sobre user_id, a segunda equipe nao gravaria.
        Long marina = transacao.execute(s -> UserBuilder.anAgent().persistIn(em).getId());
        Team suporte = transacao.execute(s -> TeamBuilder.aTeam().withMember(marina).persistIn(em));
        Team infra = transacao.execute(s -> TeamBuilder.aTeam().withLead(marina).persistIn(em));

        assertThat(vinculos.find(suporte.getId(), marina)).map(TeamMembership::getRole).contains(TeamRole.MEMBER);
        assertThat(vinculos.find(infra.getId(), marina)).map(TeamMembership::getRole).contains(TeamRole.LEAD);

        // Sair de uma nao mexe na outra.
        transacao.executeWithoutResult(s -> vinculos.delete(vinculos.find(suporte.getId(), marina).orElseThrow()));

        assertThat(vinculos.find(suporte.getId(), marina)).isEmpty();
        assertThat(vinculos.find(infra.getId(), marina)).isPresent();
    }

    @Test
    @DisplayName("a busca por nome ignora a caixa, como o indice unico")
    void buscaPorNomeIgnoraCaixa() {
        String nome = TeamBuilder.PREFIXO + "Suporte Caixa";
        transacao.execute(s -> TeamBuilder.aTeam().named(nome).persistIn(em));

        assertThat(equipes.existsByName(nome.toUpperCase(Locale.ROOT))).isTrue();
        assertThat(equipes.existsByName(nome.toLowerCase(Locale.ROOT))).isTrue();
        assertThat(equipes.existsByName(TeamBuilder.PREFIXO + "Outra")).isFalse();
    }

    @Test
    @DisplayName("os vinculos de uma equipe voltam na ordem de entrada, sem os de outra")
    void vinculosDaEquipeEmOrdem() {
        Long primeiro = transacao.execute(s -> UserBuilder.anAgent().persistIn(em).getId());
        Long segundo = transacao.execute(s -> UserBuilder.anAgent().persistIn(em).getId());
        Long deOutra = transacao.execute(s -> UserBuilder.anAgent().persistIn(em).getId());
        Team equipe = transacao.execute(s ->
                TeamBuilder.aTeam().withLead(primeiro).withMember(segundo).persistIn(em));
        transacao.execute(s -> TeamBuilder.aTeam().withMember(deOutra).persistIn(em));

        assertThat(vinculos.findByTeam(equipe.getId()))
                .extracting(TeamMembership::getUserId, TeamMembership::getRole)
                .containsExactly(
                        tuple(primeiro, TeamRole.LEAD),
                        tuple(segundo, TeamRole.MEMBER));
    }

    @Test
    @DisplayName("a troca de papel chega ao banco pelo save do repositorio")
    void trocaDePapelChegaAoBanco() {
        Long pessoa = transacao.execute(s -> UserBuilder.anAgent().persistIn(em).getId());
        Team equipe = transacao.execute(s -> TeamBuilder.aTeam().withMember(pessoa).persistIn(em));

        transacao.executeWithoutResult(s -> {
            TeamMembership vinculo = vinculos.find(equipe.getId(), pessoa).orElseThrow();
            vinculo.changeRole(TeamRole.LEAD);
            vinculos.save(vinculo);
        });

        // Lido por SQL, e nao pelo repositorio: e o valor gravado que o CHECK da V2 aceitou.
        assertThat(jdbc.sql("SELECT role FROM team_memberships WHERE team_id = :t AND user_id = :u")
                        .param("t", equipe.getId()).param("u", pessoa).query(String.class).single())
                .isEqualTo("LEAD");
    }

    @Test
    @DisplayName("nome e descricao nos limites da V2 cabem nas colunas")
    void larguraDasColunas() {
        String nome = TeamBuilder.PREFIXO + "n".repeat(100 - TeamBuilder.PREFIXO.length());
        Team gravada = transacao.execute(s -> equipes.save(new Team(nome, "d".repeat(500))));

        assertThat(equipes.findById(gravada.getId())).map(Team::getName).contains(nome);
    }
}
