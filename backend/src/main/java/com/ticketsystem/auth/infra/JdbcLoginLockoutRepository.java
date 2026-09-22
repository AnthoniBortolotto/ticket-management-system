package com.ticketsystem.auth.infra;

import com.ticketsystem.auth.domain.LoginLockout;
import com.ticketsystem.auth.domain.LoginLockoutRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Tentativas de login em JDBC, e nao em JPA.
 *
 * <p>O motivo e a escrita: o incremento precisa ser atomico, e isso e um
 * {@code INSERT ... ON CONFLICT DO UPDATE} com incremento relativo. Um find-and-save em Java
 * perde uma contagem quando duas tentativas falham ao mesmo tempo, e a primeira falha de
 * duas requisicoes concorrentes quebraria por violacao de chave primaria. Com a escrita
 * em SQL, manter uma entidade JPA so para a leitura deixaria o cache de primeiro nivel
 * mentindo sobre o estado da linha.
 *
 * <p>O {@link JdbcClient} participa da transacao do Spring: o JpaTransactionManager expoe
 * a mesma conexao ao JDBC, entao o {@code REQUIRES_NEW} do service vale aqui tambem.
 *
 * <p>O instante sempre chega de fora, do {@code Clock} do service. Nada aqui usa
 * {@code now()} do banco — senao o teste nao teria como controlar o tempo.
 */
@Repository
class JdbcLoginLockoutRepository implements LoginLockoutRepository {

    private final JdbcClient jdbc;

    JdbcLoginLockoutRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LoginLockout> findByUserId(Long userId) {
        return jdbc.sql(
                        """
                        SELECT user_id, failed_attempts, locked_until
                        FROM login_lockouts
                        WHERE user_id = :usuario
                        """)
                .param("usuario", userId)
                .query((linha, n) -> new LoginLockout(
                        linha.getLong("user_id"),
                        linha.getInt("failed_attempts"),
                        instante(linha.getObject("locked_until", OffsetDateTime.class))))
                .optional();
    }

    @Override
    public int registerFailure(Long userId, Instant failedAt, Instant forgetFailuresBefore) {
        // A condicao de recomeco aparece duas vezes, uma por coluna: o Postgres avalia o
        // SET contra a linha ANTIGA, entao as duas colunas enxergam o mesmo estado e nao
        // ha ordem de avaliacao para se preocupar.
        return jdbc.sql(
                        """
                        INSERT INTO login_lockouts (user_id, failed_attempts, last_failed_at)
                        VALUES (:usuario, 1, :agora)
                        ON CONFLICT (user_id) DO UPDATE SET
                            failed_attempts = CASE
                                WHEN login_lockouts.locked_until <= :agora
                                  OR login_lockouts.last_failed_at < :esquecerAntes
                                  OR login_lockouts.last_failed_at IS NULL
                                THEN 1
                                ELSE login_lockouts.failed_attempts + 1
                            END,
                            locked_until = CASE
                                WHEN login_lockouts.locked_until <= :agora
                                  OR login_lockouts.last_failed_at < :esquecerAntes
                                  OR login_lockouts.last_failed_at IS NULL
                                THEN NULL
                                ELSE login_lockouts.locked_until
                            END,
                            last_failed_at = :agora
                        RETURNING failed_attempts
                        """)
                .param("usuario", userId)
                .param("agora", comoTimestamp(failedAt))
                .param("esquecerAntes", comoTimestamp(forgetFailuresBefore))
                .query(Integer.class)
                .single();
    }

    @Override
    public void lockUntil(Long userId, Instant until) {
        jdbc.sql("UPDATE login_lockouts SET locked_until = :ate WHERE user_id = :usuario")
                .param("ate", comoTimestamp(until))
                .param("usuario", userId)
                .update();
    }

    @Override
    public void clear(Long userId) {
        jdbc.sql("DELETE FROM login_lockouts WHERE user_id = :usuario")
                .param("usuario", userId)
                .update();
    }

    /**
     * O driver do Postgres nao infere o tipo SQL de um {@link Instant} em JDBC cru — quem
     * faz isso pelas entidades e o Hibernate. {@code OffsetDateTime} em UTC ele entende.
     */
    private static OffsetDateTime comoTimestamp(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }

    private static Instant instante(OffsetDateTime valor) {
        return valor == null ? null : valor.toInstant();
    }
}
