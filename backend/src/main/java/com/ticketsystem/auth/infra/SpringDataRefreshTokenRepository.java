package com.ticketsystem.auth.infra;

import com.ticketsystem.auth.domain.RefreshToken;
import com.ticketsystem.auth.domain.RevocationReason;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data para refresh tokens. Detalhe de implementacao: o service nao a enxerga. */
interface SpringDataRefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** {@code SELECT ... FOR UPDATE}: ver o motivo em {@code RefreshTokenRepository}. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revoga os tokens vivos de uma familia.
     *
     * <p>Update em massa nao passa pela auditoria do Spring Data, entao o
     * {@code updatedAt} vai na propria query — senao a coluna mentiria sobre a ultima
     * mudanca de estado do token. {@code clearAutomatically} descarta o que estiver em
     * memoria depois, para ninguem ler uma entidade que o banco ja mudou por baixo.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE RefreshToken t
               SET t.revokedAt = :em, t.revokedReason = :motivo, t.updatedAt = :em
             WHERE t.familyId = :familia AND t.revokedAt IS NULL
            """)
    int revokeFamily(
            @Param("familia") UUID familyId,
            @Param("em") Instant at,
            @Param("motivo") RevocationReason reason);
}
