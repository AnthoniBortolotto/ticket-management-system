package com.ticketsystem.user.web.v1.dto;

import com.ticketsystem.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dados para criar um usuario.
 *
 * <p>Os tamanhos repetem as larguras da migration V2 (320 e 150), para a recusa sair
 * como 400 com o campo apontado em vez de um erro do banco.
 *
 * <p>A senha tem minimo de 8 caracteres aqui, e o maximo de 72 <em>bytes</em> e conferido
 * no service: {@code @Size} conta caracteres, e 40 letras acentuadas ja passam do limite
 * do BCrypt.
 */
public record CreateUserRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull UserRole role) {

    @Override
    public String toString() {
        return "CreateUserRequest[email=%s, role=%s]".formatted(email, role);
    }
}
