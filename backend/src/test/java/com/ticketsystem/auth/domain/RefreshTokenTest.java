package com.ticketsystem.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** O ciclo de vida de um refresh token: valido, usado, revogado, expirado. */
class RefreshTokenTest {

    private static final Instant EMISSAO = Instant.parse("2026-09-22T10:00:00Z");
    private static final Instant VALIDADE = EMISSAO.plus(Duration.ofDays(7));
    private static final String HASH =
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    @Test
    @DisplayName("um token recem-emitido nao foi consumido")
    void recemEmitidoNaoFoiConsumido() {
        assertThat(novo().wasConsumed()).isFalse();
    }

    @Test
    @DisplayName("antes da validade, o token nao expirou")
    void antesDaValidadeNaoExpirou() {
        assertThat(novo().isExpiredAt(VALIDADE.minusMillis(1))).isFalse();
    }

    @Test
    @DisplayName("no instante exato da validade, o token ja expirou")
    void noInstanteDaValidadeJaExpirou() {
        // A borda e exclusiva, como no bloqueio de conta: "valido ate X" deixa de valer em
        // X. Fixar isso aqui e o que pega um `isAfter` trocado por `isBefore`.
        assertThat(novo().isExpiredAt(VALIDADE)).isTrue();
    }

    @Test
    @DisplayName("marcar como usado consome o token")
    void marcarComoUsadoConsome() {
        var token = novo();

        token.markUsed(EMISSAO.plusSeconds(60));

        assertThat(token.wasConsumed()).isTrue();
        assertThat(token.getUsedAt()).isEqualTo(EMISSAO.plusSeconds(60));
    }

    @Test
    @DisplayName("um token usado nao pode ser usado de novo")
    void tokenUsadoNaoEhReusado() {
        var token = novo();
        token.markUsed(EMISSAO.plusSeconds(60));

        // Chegar aqui significaria que o service deixou passar um replay: e erro de
        // programacao, nao de entrada, por isso excecao de estado e nao de dominio.
        assertThatThrownBy(() -> token.markUsed(EMISSAO.plusSeconds(120)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("revogar consome o token e guarda o motivo")
    void revogarConsomeEGuardaMotivo() {
        var token = novo();

        token.revoke(EMISSAO.plusSeconds(30), RevocationReason.LOGOUT);

        assertThat(token.wasConsumed()).isTrue();
        assertThat(token.getRevokedReason()).isEqualTo(RevocationReason.LOGOUT);
    }

    @Test
    @DisplayName("revogar de novo nao reescreve quando nem por que")
    void revogarDuasVezesMantemAPrimeira() {
        var token = novo();
        token.revoke(EMISSAO.plusSeconds(30), RevocationReason.LOGOUT);

        token.revoke(EMISSAO.plusSeconds(90), RevocationReason.REUSE_DETECTED);

        // O primeiro motivo e o que conta a historia. Sobrescreve-lo apagaria a
        // informacao de por que a sessao terminou.
        assertThat(token.getRevokedAt()).isEqualTo(EMISSAO.plusSeconds(30));
        assertThat(token.getRevokedReason()).isEqualTo(RevocationReason.LOGOUT);
    }

    @Test
    @DisplayName("o hash nao aparece no toString")
    void hashNaoVazaNoToString() {
        // toString de entidade acaba em log de erro e em mensagem de excecao. O hash nao
        // deixa entrar na sessao, mas e o identificador dela: nao tem por que estar num log.
        assertThat(novo().toString()).doesNotContain(HASH).contains("RefreshToken");
    }

    @Test
    @DisplayName("hash com formato errado nao cria token")
    void hashForaDoFormatoNaoCria() {
        // A mesma regra do CHECK da V4, aqui para falhar antes do banco: guardar o token
        // cru no lugar do hash e o erro silencioso que ela existe para pegar.
        assertThatThrownBy(
                        () -> RefreshToken.issue(1L, "token-cru-nao-e-hash", UUID.randomUUID(), VALIDADE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RefreshToken novo() {
        return RefreshToken.issue(1L, HASH, UUID.randomUUID(), VALIDADE);
    }
}
