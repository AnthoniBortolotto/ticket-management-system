package com.ticketsystem.auth.web.v1.dto;

import com.ticketsystem.auth.service.Tokens;

/**
 * O par de tokens entregue ao cliente.
 *
 * <p>{@code expiresInSeconds} em vez de um instante absoluto: o relogio do cliente pode
 * estar errado, e uma duracao nao depende dele.
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds) {

    public static TokenResponse from(Tokens tokens) {
        return new TokenResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                "Bearer",
                tokens.accessTokenTtl().toSeconds());
    }

    @Override
    public String toString() {
        return "TokenResponse[tokenType=%s, expiresInSeconds=%d]".formatted(tokenType, expiresInSeconds);
    }
}
