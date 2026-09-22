package com.ticketsystem.auth.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** O que o dominio precisa guardar e perguntar sobre refresh tokens. */
public interface RefreshTokenRepository {

    /**
     * Busca o token para renova-lo, travando-o ate o fim da transacao.
     *
     * <p>A trava nao e detalhe: sem ela, duas renovacoes simultaneas do mesmo token leriam
     * as duas "ainda nao usado" e emitiriam dois tokens novos na mesma familia — e a
     * deteccao de reuso nunca dispararia, porque cada uma achou que era a primeira.
     */
    Optional<RefreshToken> findForRotation(String tokenHash);

    RefreshToken save(RefreshToken token);

    /**
     * Revoga todos os tokens ainda vivos de uma familia. Os ja revogados ficam como estao,
     * com o motivo original.
     */
    int revokeFamily(UUID familyId, Instant at, RevocationReason reason);
}
