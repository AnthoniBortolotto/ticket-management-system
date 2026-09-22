package com.ticketsystem.user;

/**
 * Papel global do usuario no sistema.
 *
 * <p>Fica na raiz do modulo, e nao em {@code user.domain}, de proposito: {@code auth}
 * precisa do papel para montar o claim do token e a fachada o devolve dentro de
 * {@code UserAccount}. Pelo Modulith, o que esta em subpacote e interno — {@code auth}
 * importando {@code user.domain.UserRole} quebraria o build, e nao adianta "so ler o
 * valor", porque a referencia fica no bytecode.
 *
 * <p>Isso diverge do que o CODEBASE-MAP planejava, e e a decisao certa: o papel ja e
 * contrato publicado de qualquer forma — aparece no CHECK da migration V2, no claim do
 * JWT e no schema OpenAPI de onde saem os tipos do frontend.
 *
 * <p>Os nomes casam com o CHECK {@code users_role_check} da V2. Um valor novo aqui exige
 * migration que afrouxe aquele CHECK — deliberado, para a mudanca aparecer num diff.
 */
public enum UserRole {

    /** Abre chamados e acompanha os proprios. Nunca ve comentario interno. */
    REQUESTER,

    /** Atende chamados das equipes de que participa. */
    AGENT,

    /** Enxerga e faz tudo. */
    ADMIN
}
