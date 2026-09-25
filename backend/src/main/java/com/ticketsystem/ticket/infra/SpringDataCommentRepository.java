package com.ticketsystem.ticket.infra;

import com.ticketsystem.ticket.domain.Comment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Detalhe de implementacao. As duas consultas usam o indice {@code (ticket_id, id)} da V5.
 *
 * <p>A versao publica filtra {@code internal = false} no SQL. E ela, e nao um {@code filter}
 * depois, que garante que a nota interna nunca sai do banco para quem nao atende.
 */
interface SpringDataCommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByTicketIdOrderByIdAsc(Long ticketId);

    List<Comment> findByTicketIdAndInternalFalseOrderByIdAsc(Long ticketId);
}
