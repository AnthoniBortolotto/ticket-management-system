package com.ticketsystem.user.web.v1.dto;

import com.ticketsystem.user.UserAccount;
import com.ticketsystem.user.UserRole;

/** Um usuario como a API o mostra: sem hash de senha, sem estado de bloqueio. */
public record UserResponse(Long id, String email, String fullName, UserRole role) {

    public static UserResponse from(UserAccount conta) {
        return new UserResponse(conta.id(), conta.email(), conta.fullName(), conta.role());
    }
}
