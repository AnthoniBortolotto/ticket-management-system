package com.ticketsystem.ticket.web.v1.dto;

import com.ticketsystem.ticket.domain.Comment;
import java.time.Instant;

/** Um comentario como a API o mostra. Quem nao atende o ticket so recebe os publicos. */
public record CommentResponse(Long id, Long authorId, String body, boolean internal, Instant createdAt) {

    public static CommentResponse from(Comment comentario) {
        return new CommentResponse(
                comentario.getId(),
                comentario.getAuthorId(),
                comentario.getBody(),
                comentario.isInternal(),
                comentario.getCreatedAt());
    }
}
