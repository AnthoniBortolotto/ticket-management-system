package com.ticketsystem.team.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TeamTest {

    @Test
    @DisplayName("o nome e guardado sem espacos nas pontas e com a caixa digitada")
    void nomeGuardaACaixaSemEspacos() {
        // A unicidade ignora a caixa (indice sobre lower(name)), mas o nome e texto de
        // exibicao: "Suporte N1" nao pode virar "suporte n1".
        assertThat(new Team("  Suporte N1 ", null).getName()).isEqualTo("Suporte N1");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("nome em branco e recusado")
    void nomeEmBrancoEhRecusado(String nome) {
        assertThatThrownBy(() -> new Team(nome, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("descricao e opcional, e em branco vira ausente")
    void descricaoEmBrancoViraAusente() {
        // Sem isto, "sem descricao" teria duas representacoes no banco — NULL e '' — e
        // toda consulta futura precisaria lembrar das duas.
        assertThat(new Team("Infra", null).getDescription()).isNull();
        assertThat(new Team("Infra", "   ").getDescription()).isNull();
        assertThat(new Team("Infra", " Rede e acessos ").getDescription()).isEqualTo("Rede e acessos");
    }
}
