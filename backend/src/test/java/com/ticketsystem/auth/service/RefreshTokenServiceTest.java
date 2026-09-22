package com.ticketsystem.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketsystem.auth.domain.RefreshToken;
import com.ticketsystem.auth.domain.RefreshTokenRepository;
import com.ticketsystem.auth.domain.RevocationReason;
import com.ticketsystem.common.error.ProblemKind;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Emissao, rotacao e revogacao de refresh tokens, com o repositorio mockado.
 *
 * <p>Que a revogacao por reuso sobreviva a excecao lancada logo depois — o
 * {@code noRollbackFor} — so da para provar com transacao de verdade, no teste de
 * integracao do login.
 */
class RefreshTokenServiceTest {

    private static final Instant AGORA = Instant.parse("2026-09-22T10:00:00Z");
    private static final Duration VALIDADE = Duration.ofDays(7);

    private final RefreshTokenRepository repositorio = mock(RefreshTokenRepository.class);
    private final RefreshTokenService service = new RefreshTokenService(
            repositorio, propriedades(), Clock.fixed(AGORA, ZoneOffset.UTC));

    @Test
    @DisplayName("o banco recebe o hash do token, nunca o valor entregue ao cliente")
    void gravaOHashNuncaOValor() {
        var emitido = service.issue(7L);

        RefreshToken gravado = capturarGravado();
        // Guardar o valor cru funcionaria em todos os fluxos, e o banco viraria um cofre
        // de sessoes vivas para quem lesse um dump.
        assertThat(emitido.value()).isNotBlank();
        assertThat(campoHash(gravado))
                .isNotEqualTo(emitido.value())
                .isEqualTo(RefreshTokenService.hash(emitido.value()));
    }

    @Test
    @DisplayName("o token vale pela duracao configurada, contada do relogio")
    void validadeVemDaConfiguracao() {
        var emitido = service.issue(7L);

        assertThat(emitido.expiresAt()).isEqualTo(AGORA.plus(VALIDADE));
        assertThat(emitido.userId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("cada login abre uma familia nova")
    void cadaLoginAbreFamiliaNova() {
        service.issue(7L);
        service.issue(7L);

        ArgumentCaptor<RefreshToken> gravados = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repositorio, org.mockito.Mockito.times(2)).save(gravados.capture());
        // Dois dispositivos, duas familias: um roubo detectado em um nao derruba o outro.
        assertThat(gravados.getAllValues().get(0).getFamilyId())
                .isNotEqualTo(gravados.getAllValues().get(1).getFamilyId());
    }

    @Test
    @DisplayName("dois tokens emitidos nunca coincidem")
    void tokensNaoCoincidem() {
        assertThat(service.issue(7L).value()).isNotEqualTo(service.issue(7L).value());
    }

    @Test
    @DisplayName("renovar consome o token atual e emite outro na mesma familia")
    void renovarConsomeEEmiteNaMesmaFamilia() {
        UUID familia = UUID.randomUUID();
        RefreshToken atual = RefreshToken.issue(7L, RefreshTokenService.hash("valor-atual"), familia, AGORA.plus(VALIDADE));
        when(repositorio.findForRotation(RefreshTokenService.hash("valor-atual"))).thenReturn(Optional.of(atual));

        var novo = service.rotate("valor-atual");

        assertThat(atual.getUsedAt()).isEqualTo(AGORA);
        assertThat(novo.value()).isNotEqualTo("valor-atual");
        assertThat(novo.userId()).isEqualTo(7L);
        assertThat(capturarGravado().getFamilyId()).isEqualTo(familia);
    }

    @Test
    @DisplayName("token desconhecido nao renova")
    void tokenDesconhecidoNaoRenova() {
        when(repositorio.findForRotation(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("inventado"))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .extracting(e -> ((InvalidRefreshTokenException) e).kind())
                .isEqualTo(ProblemKind.UNAUTHORIZED);
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("token ja usado apresentado de novo derruba a familia inteira")
    void reusoDerrubaAFamilia() {
        UUID familia = UUID.randomUUID();
        RefreshToken usado = RefreshToken.issue(7L, RefreshTokenService.hash("copiado"), familia, AGORA.plus(VALIDADE));
        usado.markUsed(AGORA.minusSeconds(60));
        when(repositorio.findForRotation(RefreshTokenService.hash("copiado"))).thenReturn(Optional.of(usado));

        assertThatThrownBy(() -> service.rotate("copiado")).isInstanceOf(InvalidRefreshTokenException.class);

        // Um token consumido so reaparece se alguem guardou uma copia. Revogar so a linha
        // apresentada nao adiantaria: o token que o ladrao ja trocou continuaria vivo.
        verify(repositorio).revokeFamily(familia, AGORA, RevocationReason.REUSE_DETECTED);
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("token expirado nao renova, e nao e tratado como roubo")
    void tokenExpiradoNaoRenova() {
        RefreshToken vencido = RefreshToken.issue(7L, RefreshTokenService.hash("antigo"), UUID.randomUUID(), AGORA);
        when(repositorio.findForRotation(RefreshTokenService.hash("antigo"))).thenReturn(Optional.of(vencido));

        assertThatThrownBy(() -> service.rotate("antigo")).isInstanceOf(InvalidRefreshTokenException.class);

        // Expirar e o fim normal de uma sessao. Tratar como reuso derrubaria a familia de
        // quem so ficou uma semana sem abrir o sistema.
        verify(repositorio, never()).revokeFamily(any(), any(), any());
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("logout revoga a sessao inteira")
    void logoutRevogaASessao() {
        UUID familia = UUID.randomUUID();
        RefreshToken atual = RefreshToken.issue(7L, RefreshTokenService.hash("sessao"), familia, AGORA.plus(VALIDADE));
        when(repositorio.findForRotation(RefreshTokenService.hash("sessao"))).thenReturn(Optional.of(atual));

        service.revoke("sessao");

        verify(repositorio).revokeFamily(familia, AGORA, RevocationReason.LOGOUT);
    }

    @Test
    @DisplayName("logout com token desconhecido nao falha, nem diz que nao conhecia")
    void logoutComTokenDesconhecidoEhSilencioso() {
        when(repositorio.findForRotation(any())).thenReturn(Optional.empty());

        // Erro aqui diria a quem chama se o token existia. Logout e idempotente.
        service.revoke("qualquer");

        verify(repositorio, never()).revokeFamily(any(), any(), any());
    }

    @Test
    @DisplayName("o hash e SHA-256 em hexadecimal minusculo, o mesmo formato do CHECK da V4")
    void hashNoFormatoDoBanco() {
        assertThat(RefreshTokenService.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    private RefreshToken capturarGravado() {
        ArgumentCaptor<RefreshToken> gravado = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repositorio).save(gravado.capture());
        return gravado.getValue();
    }

    /** O hash nao tem getter publico de proposito; aqui o reflection faz o papel do banco. */
    private static String campoHash(RefreshToken token) {
        return (String) org.springframework.test.util.ReflectionTestUtils.getField(token, "tokenHash");
    }

    private static AuthProperties propriedades() {
        return new AuthProperties(
                "um-segredo-de-teste-com-mais-de-32-bytes-de-tamanho",
                "ticket-system",
                Duration.ofMinutes(30),
                VALIDADE,
                3,
                Duration.ofMinutes(15));
    }
}
