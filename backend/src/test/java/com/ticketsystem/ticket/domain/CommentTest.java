package com.ticketsystem.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CommentTest {

    @Test
    @DisplayName("resposta publica e nota interna diferem so na flag")
    void publicoEInterno() {
        Comment resposta = Comment.publicReply(1L, 2L, "Pode reiniciar?");
        Comment nota = Comment.internalNote(1L, 2L, "Provavel problema de DNS.");

        assertThat(resposta.isInternal()).isFalse();
        assertThat(nota.isInternal()).isTrue();
        assertThat(nota.getTicketId()).isEqualTo(1L);
        assertThat(nota.getAuthorId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("o corpo e guardado sem os espacos das pontas")
    void corpoEhAparado() {
        assertThat(Comment.publicReply(1L, 2L, "  ok  ").getBody()).isEqualTo("ok");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("corpo em branco e recusado")
    void corpoEmBrancoEhRecusado(String corpo) {
        assertThatThrownBy(() -> Comment.publicReply(1L, 2L, corpo)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("toString diz se e interno, mas nao carrega o corpo")
    void toStringSemCorpo() {
        // Nota interna num log e vazamento com outro nome.
        assertThat(Comment.internalNote(1L, 2L, "Cliente e suspeito de fraude").toString())
                .contains("internal=true").doesNotContain("fraude");
    }

    @Test
    @DisplayName("sem ticket ou sem autor, o comentario nao nasce")
    void semAsDuasPontas() {
        assertThatThrownBy(() -> Comment.internalNote(null, 2L, "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Comment.internalNote(1L, null, "x")).isInstanceOf(IllegalArgumentException.class);
    }
}
