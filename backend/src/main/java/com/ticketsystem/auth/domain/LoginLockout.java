package com.ticketsystem.auth.domain;

import java.time.Instant;

/**
 * O estado de tentativas fracassadas de login de uma conta.
 *
 * <p>Nao e entidade JPA, de proposito. Toda escrita nesta tabela e um
 * {@code INSERT ... ON CONFLICT DO UPDATE} atomico — incremento relativo no SQL, que nao
 * perde contagem quando duas tentativas falham ao mesmo tempo — e isso nao passa pelo
 * {@code save()} do JPA. Com entidade, metade das escritas seria JPA e metade SQL na mao,
 * e o cache de primeiro nivel mentiria sobre o estado da linha. O adaptador em
 * {@code auth.infra} e JDBC, e esta classe e so o valor lido.
 *
 * <p>"Esta bloqueada?" e decidido aqui, em Java, com o instante recebido de fora — nunca
 * com {@code now()} dentro do SQL. Regra em SQL nao e alcancavel por teste unitario nem
 * pelo PITest.
 *
 * @param lockedUntil nulo quando a conta nao esta bloqueada
 */
public record LoginLockout(Long userId, int failedAttempts, Instant lockedUntil) {

    /**
     * Se a conta esta bloqueada em {@code instant}.
     *
     * <p>"Bloqueada ate as 10:15" libera as 10:15 em ponto: o fim e exclusivo.
     */
    public boolean isLockedAt(Instant instant) {
        return lockedUntil != null && instant.isBefore(lockedUntil);
    }
}
