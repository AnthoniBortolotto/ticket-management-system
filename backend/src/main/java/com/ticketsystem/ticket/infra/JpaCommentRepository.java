package com.ticketsystem.ticket.infra;

import com.ticketsystem.ticket.domain.Comment;
import com.ticketsystem.ticket.domain.CommentRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Adapta o Spring Data a porta de comentarios. */
@Repository
class JpaCommentRepository implements CommentRepository {

    private final SpringDataCommentRepository springData;

    JpaCommentRepository(SpringDataCommentRepository springData) {
        this.springData = springData;
    }

    @Override
    public Comment save(Comment comment) {
        return springData.save(comment);
    }

    @Override
    public List<Comment> findByTicket(Long ticketId) {
        return springData.findByTicketIdOrderByIdAsc(ticketId);
    }

    @Override
    public List<Comment> findPublicByTicket(Long ticketId) {
        return springData.findByTicketIdAndInternalFalseOrderByIdAsc(ticketId);
    }
}
