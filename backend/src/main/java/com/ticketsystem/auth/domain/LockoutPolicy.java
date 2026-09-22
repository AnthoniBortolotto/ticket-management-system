package com.ticketsystem.auth.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * A regra do bloqueio: quantas falhas seguidas bloqueiam a conta, e por quanto tempo.
 *
 * <p>Os valores vem de configuracao, e nao do banco: limiar de bloqueio e parametro
 * operacional de seguranca, que muda com deploy — diferente de prazo de SLA, que e dado de
 * negocio editado por um admin em runtime.
 *
 * <p>O bloqueio e curto e expira sozinho, de proposito. Bloqueio por conta tem um custo
 * conhecido: quem souber o e-mail de alguem pode mante-lo bloqueado errando a senha. Uma
 * janela que vence sem intervencao limita esse estrago; um bloqueio permanente o
 * transformaria em negacao de servico.
 */
public record LockoutPolicy(int maxFailedAttempts, Duration lockDuration) {

    public LockoutPolicy {
        if (maxFailedAttempts < 1) {
            throw new IllegalArgumentException(
                    "o limite de tentativas precisa ser ao menos 1; zero bloquearia todo mundo");
        }
        if (lockDuration == null || lockDuration.isZero() || lockDuration.isNegative()) {
            throw new IllegalArgumentException("a duracao do bloqueio precisa ser positiva");
        }
    }

    /**
     * Ate quando a conta fica bloqueada depois de {@code failedAttempts} falhas seguidas.
     *
     * <p>Vazio se ainda nao atingiu o limite. O limite e inclusivo: com maximo 3, a
     * terceira falha ja bloqueia.
     */
    public Optional<Instant> lockAfter(int failedAttempts, Instant failedAt) {
        return failedAttempts >= maxFailedAttempts
                ? Optional.of(failedAt.plus(lockDuration))
                : Optional.empty();
    }
}
