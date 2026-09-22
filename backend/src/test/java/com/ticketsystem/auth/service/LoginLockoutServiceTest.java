package com.ticketsystem.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketsystem.auth.domain.LoginLockout;
import com.ticketsystem.auth.domain.LoginLockoutRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Quando o service bloqueia, libera e esquece uma conta.
 *
 * <p>Relogio parado: o instante e exato e o teste nao dorme. Que a falha registrada
 * sobreviva ao rollback do login e assunto do teste de integracao — aqui nao ha
 * transacao para provar isso.
 */
class LoginLockoutServiceTest {

    private static final Instant AGORA = Instant.parse("2026-09-22T10:00:00Z");
    private static final Duration DURACAO = Duration.ofMinutes(15);

    private final LoginLockoutRepository repositorio = mock(LoginLockoutRepository.class);
    private final LoginLockoutService service =
            new LoginLockoutService(repositorio, propriedades(3), Clock.fixed(AGORA, ZoneOffset.UTC));

    @Test
    @DisplayName("conta sem nenhuma falha nao esta bloqueada")
    void contaSemRegistroNaoEstaBloqueada() {
        when(repositorio.findByUserId(1L)).thenReturn(Optional.empty());

        assertThat(service.isLocked(1L)).isFalse();
    }

    @Test
    @DisplayName("conta com bloqueio vigente esta bloqueada")
    void bloqueioVigenteBloqueia() {
        when(repositorio.findByUserId(1L))
                .thenReturn(Optional.of(new LoginLockout(1L, 3, AGORA.plus(DURACAO))));

        assertThat(service.isLocked(1L)).isTrue();
    }

    @Test
    @DisplayName("conta cujo bloqueio ja venceu nao esta bloqueada")
    void bloqueioVencidoNaoBloqueia() {
        when(repositorio.findByUserId(1L))
                .thenReturn(Optional.of(new LoginLockout(1L, 3, AGORA.minusSeconds(1))));

        assertThat(service.isLocked(1L)).isFalse();
    }

    @Test
    @DisplayName("falha abaixo do limite conta, mas nao bloqueia")
    void falhaAbaixoDoLimiteNaoBloqueia() {
        when(repositorio.registerFailure(1L, AGORA, AGORA.minus(DURACAO))).thenReturn(2);

        service.registerFailure(1L);

        verify(repositorio, never()).lockUntil(anyLong(), any());
    }

    @Test
    @DisplayName("a falha que atinge o limite bloqueia a conta pela duracao configurada")
    void falhaQueAtingeOLimiteBloqueia() {
        when(repositorio.registerFailure(1L, AGORA, AGORA.minus(DURACAO))).thenReturn(3);

        service.registerFailure(1L);

        verify(repositorio).lockUntil(1L, AGORA.plus(DURACAO));
    }

    @Test
    @DisplayName("falhas mais antigas que a duracao do bloqueio sao esquecidas")
    void janelaDeEsquecimentoEhADuracaoDoBloqueio() {
        when(repositorio.registerFailure(1L, AGORA, AGORA.minus(DURACAO))).thenReturn(1);

        service.registerFailure(1L);

        // A mesma janela para os dois: uma falha que ja seria "bloqueio vencido" nao pode
        // continuar contando como "falha recente".
        verify(repositorio).registerFailure(1L, AGORA, AGORA.minus(DURACAO));
    }

    @Test
    @DisplayName("login bem-sucedido apaga o historico de falhas")
    void sucessoApagaHistorico() {
        service.registerSuccess(1L);

        verify(repositorio).clear(1L);
    }

    private static AuthProperties propriedades(int maximo) {
        return new AuthProperties(
                "um-segredo-de-teste-com-mais-de-32-bytes-de-tamanho",
                "ticket-system",
                Duration.ofMinutes(30),
                Duration.ofDays(7),
                maximo,
                DURACAO);
    }
}
