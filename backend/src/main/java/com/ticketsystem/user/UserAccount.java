package com.ticketsystem.user;

/**
 * O que os outros modulos podem saber sobre um usuario.
 *
 * <p>Repare no que <strong>nao</strong> esta aqui: o hash da senha. Ele nao atravessa a
 * fronteira do modulo. Quem precisa conferir credencial chama
 * {@code UserFacade.authenticate} e recebe isto — ou nada.
 *
 * <p>E um record, e nao a entidade {@code User}: entidade JPA que circula entre modulos
 * carrega estado de persistencia junto e amarra quem a recebe ao armazenamento atual.
 */
public record UserAccount(Long id, String email, String fullName, UserRole role) {
}
