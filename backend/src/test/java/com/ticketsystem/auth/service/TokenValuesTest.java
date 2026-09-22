package com.ticketsystem.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Os records que carregam credencial nao a imprimem.
 *
 * <p>Record gera um {@code toString} com todos os campos. Para {@link Tokens} e
 * {@link IssuedRefreshToken} isso significaria um refresh token de sete dias num log de
 * DEBUG, numa mensagem de excecao ou num dump de variaveis — credencial de sessao viva em
 * texto para quem tiver acesso ao log. Os dois sobrescrevem o {@code toString}; este teste
 * e o que impede alguem de "simplificar" apagando a sobrescrita.
 */
class TokenValuesTest {

    @Test
    @DisplayName("o par de tokens nao imprime nenhum dos dois")
    void tokensNaoImprimemCredencial() {
        var tokens = new Tokens("access-secreto", "refresh-secreto", Duration.ofMinutes(30));

        assertThat(tokens.toString())
                .doesNotContain("access-secreto")
                .doesNotContain("refresh-secreto")
                .contains("PT30M");
    }

    @Test
    @DisplayName("o refresh token emitido nao imprime o valor")
    void refreshEmitidoNaoImprimeValor() {
        var emitido = new IssuedRefreshToken(7L, "valor-secreto", Instant.parse("2026-09-29T10:00:00Z"));

        assertThat(emitido.toString()).doesNotContain("valor-secreto").contains("userId=7");
    }
}
