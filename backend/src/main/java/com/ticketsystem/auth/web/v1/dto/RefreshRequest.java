package com.ticketsystem.auth.web.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * O refresh token, no corpo e nao na URL: URL acaba em log de proxy e em historico.
 *
 * <p>{@code toString} sobrescrito porque o record imprimiria a credencial.
 */
public record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {

    @Override
    public String toString() {
        return "RefreshRequest[]";
    }
}
