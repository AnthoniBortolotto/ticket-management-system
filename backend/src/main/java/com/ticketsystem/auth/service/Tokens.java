package com.ticketsystem.auth.service;

import java.time.Duration;

/**
 * O par de tokens de uma sessao.
 *
 * <p>{@code toString} sobrescrito: record imprimiria os dois valores, e um deles e uma
 * credencial de vida longa.
 */
public record Tokens(String accessToken, String refreshToken, Duration accessTokenTtl) {

    @Override
    public String toString() {
        return "Tokens[accessTokenTtl=%s]".formatted(accessTokenTtl);
    }
}
