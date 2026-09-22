package com.ticketsystem.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Quando uma sequencia de falhas vira bloqueio, e por quanto tempo. */
class LockoutPolicyTest {

    private static final Instant FALHA = Instant.parse("2026-09-22T10:00:00Z");

    private final LockoutPolicy politica = new LockoutPolicy(3, Duration.ofMinutes(15));

    @ParameterizedTest(name = "{0} falha(s) ainda nao bloqueiam")
    @ValueSource(ints = {1, 2})
    @DisplayName("abaixo do limite, nao bloqueia")
    void abaixoDoLimiteNaoBloqueia(int falhas) {
        assertThat(politica.lockAfter(falhas, FALHA)).isEmpty();
    }

    @Test
    @DisplayName("a falha que atinge o limite bloqueia pela duracao configurada")
    void atingirOLimiteBloqueia() {
        // O limite e inclusivo: com maximo 3, a terceira falha ja bloqueia. Um `>` no
        // lugar do `>=` daria uma tentativa a mais a quem esta forcando a senha — e
        // exatamente a mutacao de borda que o PITest existe para pegar.
        assertThat(politica.lockAfter(3, FALHA)).contains(FALHA.plus(Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("acima do limite continua bloqueando")
    void acimaDoLimiteContinuaBloqueando() {
        assertThat(politica.lockAfter(7, FALHA)).isPresent();
    }

    @Test
    @DisplayName("limite zero seria bloquear todo mundo no primeiro acesso")
    void limiteZeroEhRejeitado() {
        assertThatThrownBy(() -> new LockoutPolicy(0, Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("bloqueio sem duracao nao e bloqueio")
    void duracaoNaoPositivaEhRejeitada() {
        assertThatThrownBy(() -> new LockoutPolicy(3, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LockoutPolicy(3, Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LockoutPolicy(3, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
