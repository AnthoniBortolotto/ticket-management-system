package com.ticketsystem.auth.service;

import com.ticketsystem.auth.domain.LockoutPolicy;
import com.ticketsystem.auth.domain.LoginLockoutRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conta falhas de login, bloqueia a conta no limite e libera quando o bloqueio vence.
 */
@Service
public class LoginLockoutService {

    private final LoginLockoutRepository repositorio;
    private final LockoutPolicy politica;
    private final Clock relogio;

    LoginLockoutService(LoginLockoutRepository repositorio, AuthProperties propriedades, Clock relogio) {
        this.repositorio = repositorio;
        this.politica = new LockoutPolicy(propriedades.maxFailedAttempts(), propriedades.lockDuration());
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public boolean isLocked(Long userId) {
        Instant agora = relogio.instant();
        return repositorio.findByUserId(userId).map(registro -> registro.isLockedAt(agora)).orElse(false);
    }

    /**
     * Registra uma falha e bloqueia se o limite foi atingido.
     *
     * <p><strong>{@code REQUIRES_NEW} e o que faz o bloqueio funcionar.</strong> Quem
     * chama isto e o login, logo antes de lancar a excecao de credencial invalida. Se esta
     * escrita participasse da transacao do login, o rollback provocado pela excecao
     * desfaria a contagem — e o bloqueio nunca dispararia em producao, com a suite de
     * testes verde. Em transacao propria, a falha fica gravada aconteca o que acontecer
     * depois.
     *
     * <p>Falhas mais antigas que a duracao do bloqueio sao esquecidas: o contador significa
     * "falhas seguidas e recentes".
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerFailure(Long userId) {
        Instant agora = relogio.instant();
        int total = repositorio.registerFailure(userId, agora, agora.minus(politica.lockDuration()));
        politica.lockAfter(total, agora).ifPresent(ate -> repositorio.lockUntil(userId, ate));
    }

    @Transactional
    public void registerSuccess(Long userId) {
        repositorio.clear(userId);
    }
}
