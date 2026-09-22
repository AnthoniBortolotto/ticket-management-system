package com.ticketsystem.auth.service;

import java.time.Instant;

/**
 * Um refresh token recem-emitido, com o valor que vai para o cliente.
 *
 * <p>E o unico momento em que o valor existe fora do cliente: depois de devolvido, o
 * servidor so guarda o hash. Por isso nao ha {@code toString} com o valor — record
 * imprimiria, entao este sobrescreve.
 */
public record IssuedRefreshToken(Long userId, String value, Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuedRefreshToken[userId=%s, expiresAt=%s]".formatted(userId, expiresAt);
    }
}
