package com.ticketsystem.auth.infra;

import com.ticketsystem.auth.domain.RefreshToken;
import com.ticketsystem.auth.domain.RefreshTokenRepository;
import com.ticketsystem.auth.domain.RevocationReason;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Adapta o Spring Data para a interface de dominio. */
@Repository
class JpaRefreshTokenRepository implements RefreshTokenRepository {

    private final SpringDataRefreshTokenRepository springData;

    JpaRefreshTokenRepository(SpringDataRefreshTokenRepository springData) {
        this.springData = springData;
    }

    /**
     * {@code MANDATORY}: exige uma transacao ja aberta, e falha alto se nao houver.
     *
     * <p>Uma trava pessimista que termina junto com a chamada nao protege nada — ela
     * precisa durar ate o token ser marcado como usado. Sem transacao em volta, a busca
     * "funcionaria" e a protecao contra renovacao simultanea simplesmente nao existiria.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<RefreshToken> findForRotation(String tokenHash) {
        return springData.findByTokenHash(tokenHash);
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        return springData.save(token);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int revokeFamily(UUID familyId, Instant at, RevocationReason reason) {
        return springData.revokeFamily(familyId, at, reason);
    }
}
