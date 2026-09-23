package com.ticketsystem.team.domain;

/**
 * O papel de alguem <em>dentro de uma equipe</em> — independente do papel global.
 *
 * <p>Os nomes casam com o CHECK {@code team_memberships_role_check} da V2. Um valor novo
 * aqui exige migration que afrouxe aquele CHECK.
 *
 * <p>Fica em {@code domain}, e nao na raiz do modulo como {@code UserRole}: nenhum outro
 * modulo precisa do tipo. {@code ticket} pergunta "lidera?" a {@code TeamFacade} e recebe
 * um booleano.
 */
public enum TeamRole {

    /** Ve e atua nos tickets em modo equipe. */
    MEMBER,

    /**
     * Tudo o que o membro faz, mais gerenciar membros e enxergar os tickets que sairam da
     * equipe para o modo exclusivo.
     */
    LEAD
}
