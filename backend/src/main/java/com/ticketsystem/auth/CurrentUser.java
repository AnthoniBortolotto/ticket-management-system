package com.ticketsystem.auth;

import com.ticketsystem.user.UserRole;

/**
 * Quem esta fazendo a requisicao: o id e o papel global, como o token os trouxe.
 *
 * <p>Repare no que <strong>nao</strong> esta aqui: as equipes. Elas nao vem do token — ver
 * o ADR 0003 —, e quem precisa delas pergunta a {@code TeamFacade}, que consulta o banco a
 * cada requisicao. Tirar alguem de uma equipe vale na hora, sem esperar o token vencer.
 */
public record CurrentUser(Long id, UserRole role) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
