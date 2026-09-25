package com.ticketsystem.ticket.domain;

import java.util.List;

/**
 * A conversa de um ticket.
 *
 * <p>Sao duas consultas, e nao uma com filtro em memoria depois: {@link #findPublicByTicket}
 * exclui as notas internas <em>na query</em>. A regra do CLAUDE.md vale aqui tambem — filtrar
 * depois de carregar e o tipo de codigo em que um refactor inocente passa a devolver tudo.
 */
public interface CommentRepository {

    Comment save(Comment comment);

    /** Tudo, notas internas incluidas, na ordem em que foi escrito. So para quem atende. */
    List<Comment> findByTicket(Long ticketId);

    /** Sem as notas internas, na ordem em que foi escrito. E o que o solicitante recebe. */
    List<Comment> findPublicByTicket(Long ticketId);
}
