package com.ticketsystem.auth.domain;

/**
 * Por que uma sessao terminou antes de expirar.
 *
 * <p>Os nomes casam com o CHECK {@code refresh_tokens_revoked_reason_check} da V4. Existe
 * para distinguir "a sessao acabou" de "a sessao acabou porque alguem fez replay de um
 * token ja rotacionado" — sem isso, um incidente de roubo de token seria indistinguivel
 * de um logout num registro de auditoria.
 */
public enum RevocationReason {

    /** A propria pessoa encerrou a sessao. */
    LOGOUT,

    /** A senha mudou; toda sessao anterior deixa de valer. */
    PASSWORD_CHANGE,

    /**
     * Um token ja usado foi apresentado de novo. Alguem guardou uma copia: a familia
     * inteira e derrubada, para ladrao e vitima cairem juntos.
     */
    REUSE_DETECTED,

    /** Um administrador encerrou a sessao. */
    ADMIN
}
