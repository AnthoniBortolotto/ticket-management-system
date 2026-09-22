package com.ticketsystem.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Se uma conta esta bloqueada num instante dado.
 *
 * <p>O instante e parametro, e nao {@code Instant.now()}: e o que deixa os casos de borda
 * — exatamente no limite, um milissegundo antes, um depois — triviais de escrever, sem
 * relogio e sem espera.
 */
class LoginLockoutTest {

    private static final Instant FIM = Instant.parse("2026-09-22T10:15:00Z");

    @Test
    @DisplayName("conta sem bloqueio registrado nao esta bloqueada")
    void semBloqueioNaoEstaBloqueada() {
        var registro = new LoginLockout(1L, 2, null);

        assertThat(registro.isLockedAt(FIM)).isFalse();
    }

    @Test
    @DisplayName("antes do fim do bloqueio, esta bloqueada")
    void antesDoFimEstaBloqueada() {
        var registro = new LoginLockout(1L, 3, FIM);

        assertThat(registro.isLockedAt(FIM.minus(Duration.ofMillis(1)))).isTrue();
    }

    @Test
    @DisplayName("no instante exato do fim, o bloqueio ja acabou")
    void noInstanteDoFimJaNaoEstaBloqueada() {
        var registro = new LoginLockout(1L, 3, FIM);

        // "Bloqueado ate as 10:15" libera as 10:15. Fixar a borda aqui evita que um
        // `isAfter` virado de `isBefore` passe despercebido — o PITest acha essa mutacao.
        assertThat(registro.isLockedAt(FIM)).isFalse();
    }

    @Test
    @DisplayName("depois do fim, o bloqueio expirou sozinho")
    void depoisDoFimNaoEstaBloqueada() {
        var registro = new LoginLockout(1L, 3, FIM);

        assertThat(registro.isLockedAt(FIM.plus(Duration.ofMinutes(1)))).isFalse();
    }
}
