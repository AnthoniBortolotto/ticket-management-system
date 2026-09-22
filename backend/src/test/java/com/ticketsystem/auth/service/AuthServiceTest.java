package com.ticketsystem.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketsystem.common.error.ProblemKind;
import com.ticketsystem.user.UserAccount;
import com.ticketsystem.user.UserFacade;
import com.ticketsystem.user.UserRole;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A ordem das decisoes do login.
 *
 * <p>Ordem importa aqui mais do que em qualquer outro lugar do sistema: bloqueio antes de
 * senha, falha contada antes de responder, mesma resposta para conta inexistente e senha
 * errada. Cada uma dessas inversoes e um buraco de seguranca que nenhum teste de "o login
 * funciona" pegaria.
 */
class AuthServiceTest {

    private static final UserAccount ANA =
            new UserAccount(7L, "ana@empresa.com", "Ana Ribeiro", UserRole.AGENT);
    private static final Duration TTL = Duration.ofMinutes(30);

    private final UserFacade usuarios = mock(UserFacade.class);
    private final LoginLockoutService bloqueio = mock(LoginLockoutService.class);
    private final JwtService jwt = mock(JwtService.class);
    private final RefreshTokenService refresh = mock(RefreshTokenService.class);

    private final AuthService service =
            new AuthService(usuarios, bloqueio, jwt, refresh, propriedades());

    @Test
    @DisplayName("credencial certa devolve os dois tokens e zera o historico de falhas")
    void loginComCredencialCerta() {
        when(usuarios.findByEmail("ana@empresa.com")).thenReturn(Optional.of(ANA));
        when(usuarios.authenticate("ana@empresa.com", "certa")).thenReturn(Optional.of(ANA));
        when(jwt.issueAccessToken(7L, UserRole.AGENT)).thenReturn("access-da-ana");
        when(refresh.issue(7L)).thenReturn(new IssuedRefreshToken(7L, "refresh-da-ana", Instant.now()));

        Tokens tokens = service.login("ana@empresa.com", "certa");

        assertThat(tokens.accessToken()).isEqualTo("access-da-ana");
        assertThat(tokens.refreshToken()).isEqualTo("refresh-da-ana");
        assertThat(tokens.accessTokenTtl()).isEqualTo(TTL);
        verify(bloqueio).registerSuccess(7L);
    }

    @Test
    @DisplayName("senha errada conta uma falha e recusa")
    void senhaErradaContaFalha() {
        when(usuarios.findByEmail("ana@empresa.com")).thenReturn(Optional.of(ANA));
        when(usuarios.authenticate("ana@empresa.com", "errada")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("ana@empresa.com", "errada"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(bloqueio).registerFailure(7L);
        verify(refresh, never()).issue(anyLong());
    }

    @Test
    @DisplayName("e-mail inexistente recusa com a mesma excecao da senha errada")
    void emailInexistenteRecusaIgual() {
        when(usuarios.findByEmail("ninguem@empresa.com")).thenReturn(Optional.empty());
        when(usuarios.authenticate("ninguem@empresa.com", "qualquer")).thenReturn(Optional.empty());

        // Mesma excecao, mesmo status, mesmo texto: distinguir os dois casos na resposta
        // transformaria o login num verificador de quais e-mails tem conta.
        assertThatThrownBy(() -> service.login("ninguem@empresa.com", "qualquer"))
                .isInstanceOf(InvalidCredentialsException.class);

        // E a senha e conferida mesmo assim (contra um hash descartavel, dentro do modulo
        // user), para o tempo de resposta tambem nao entregar a diferenca.
        verify(usuarios).authenticate("ninguem@empresa.com", "qualquer");
        verify(bloqueio, never()).registerFailure(anyLong());
    }

    @Test
    @DisplayName("conta bloqueada e recusada sem nem conferir a senha")
    void contaBloqueadaNaoConfereSenha() {
        when(usuarios.findByEmail("ana@empresa.com")).thenReturn(Optional.of(ANA));
        when(bloqueio.isLocked(7L)).thenReturn(true);

        assertThatThrownBy(() -> service.login("ana@empresa.com", "certa"))
                .isInstanceOf(AccountLockedException.class)
                .extracting(e -> ((AccountLockedException) e).kind())
                .isEqualTo(ProblemKind.LOCKED);

        // Tres razoes para o bloqueio vir antes da senha: a senha certa nao pode destravar
        // uma conta bloqueada (senao o bloqueio nao protege nada); quem forca a senha nao
        // pode continuar testando palpites durante o bloqueio; e cada verificacao BCrypt
        // custa ~80ms que um atacante nao deveria poder gastar do servidor.
        verify(usuarios, never()).authenticate(anyString(), anyString());
        verify(refresh, never()).issue(anyLong());
    }

    @Test
    @DisplayName("tentativa durante o bloqueio nao estende o bloqueio")
    void tentativaDuranteBloqueioNaoConta() {
        when(usuarios.findByEmail("ana@empresa.com")).thenReturn(Optional.of(ANA));
        when(bloqueio.isLocked(7L)).thenReturn(true);

        assertThatThrownBy(() -> service.login("ana@empresa.com", "errada"))
                .isInstanceOf(AccountLockedException.class);

        // Se cada tentativa durante o bloqueio contasse, quem sabe o e-mail de alguem o
        // manteria bloqueado para sempre. Assim o bloqueio vence sozinho.
        verify(bloqueio, never()).registerFailure(anyLong());
    }

    @Test
    @DisplayName("renovar emite access token com o papel ATUAL do usuario")
    void renovarUsaPapelAtual() {
        when(refresh.rotate("refresh-antigo"))
                .thenReturn(new IssuedRefreshToken(7L, "refresh-novo", Instant.now()));
        // O papel mudou desde o login: Ana foi promovida.
        when(usuarios.findById(7L))
                .thenReturn(new UserAccount(7L, "ana@empresa.com", "Ana Ribeiro", UserRole.ADMIN));
        when(jwt.issueAccessToken(7L, UserRole.ADMIN)).thenReturn("access-de-admin");

        Tokens tokens = service.refresh("refresh-antigo");

        // O papel e lido de novo do banco a cada renovacao, e nao carregado do token
        // anterior: e isso que faz uma promocao ou um rebaixamento valer sem novo login.
        assertThat(tokens.accessToken()).isEqualTo("access-de-admin");
        assertThat(tokens.refreshToken()).isEqualTo("refresh-novo");
    }

    @Test
    @DisplayName("refresh token invalido nao emite access token")
    void refreshInvalidoNaoEmite() {
        when(refresh.rotate("inventado")).thenThrow(new InvalidRefreshTokenException());

        assertThatThrownBy(() -> service.refresh("inventado"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(jwt, never()).issueAccessToken(any(), any());
    }

    @Test
    @DisplayName("logout revoga a sessao")
    void logoutRevoga() {
        service.logout("refresh-da-ana");

        verify(refresh).revoke("refresh-da-ana");
    }

    private static AuthProperties propriedades() {
        return new AuthProperties(
                "um-segredo-de-teste-com-mais-de-32-bytes-de-tamanho",
                "ticket-system",
                TTL,
                Duration.ofDays(7),
                3,
                Duration.ofMinutes(15));
    }
}
