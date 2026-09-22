package com.ticketsystem.auth.web.v1.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credenciais de login.
 *
 * <p>Os limites de tamanho nao sao validacao de negocio — sao protecao: sem eles, uma
 * senha de megabytes custaria processamento a cada tentativa anonima. {@code toString}
 * sobrescrito porque o record imprimiria a senha.
 */
public record LoginRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 200) String password) {

    @Override
    public String toString() {
        return "LoginRequest[email=%s]".formatted(email);
    }
}
