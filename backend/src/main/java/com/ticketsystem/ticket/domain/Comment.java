package com.ticketsystem.ticket.domain;

import com.ticketsystem.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Uma mensagem na conversa de um ticket: resposta publica ou nota interna.
 *
 * <p>A nota interna e o que o solicitante <strong>nunca</strong> ve (CLAUDE.md#visibilidade).
 * Por isso nao ha construtor com um {@code boolean} solto: cada fabrica diz, no nome, qual
 * das duas coisas esta sendo criada. Um {@code true} trocado por {@code false} numa chamada
 * compila e publica uma nota interna para o cliente.
 *
 * <p>Imutavel depois de gravado: nao ha edicao nem exclusao de comentario.
 */
@Entity
@Table(name = "ticket_comments")
public class Comment extends BaseEntity {

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private Long ticketId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;

    @Column(nullable = false, length = 5000, updatable = false)
    private String body;

    @Column(nullable = false, updatable = false)
    private boolean internal;

    /** Exigido pelo JPA. Nao use no codigo da aplicacao. */
    protected Comment() {
    }

    private Comment(Long ticketId, Long authorId, String body, boolean internal) {
        if (ticketId == null || authorId == null) {
            throw new IllegalArgumentException("comentario precisa de ticket e de autor");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("comentario em branco");
        }
        this.ticketId = ticketId;
        this.authorId = authorId;
        this.body = body.trim();
        this.internal = internal;
    }

    /** Visivel para todos que enxergam o ticket, inclusive o solicitante. */
    public static Comment publicReply(Long ticketId, Long authorId, String body) {
        return new Comment(ticketId, authorId, body, false);
    }

    /** Visivel so para quem atende. O solicitante nunca a recebe. */
    public static Comment internalNote(Long ticketId, Long authorId, String body) {
        return new Comment(ticketId, authorId, body, true);
    }

    public Long getTicketId() {
        return ticketId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public String getBody() {
        return body;
    }

    public boolean isInternal() {
        return internal;
    }

    /** Sem o corpo: nota interna em log e vazamento com outro nome. */
    @Override
    public String toString() {
        return "Comment[id=%s, ticketId=%s, internal=%s]".formatted(getId(), ticketId, internal);
    }
}
