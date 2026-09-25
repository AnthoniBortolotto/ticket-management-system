package com.ticketsystem.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ticketsystem.auth.CurrentUser;
import com.ticketsystem.common.error.DomainException;
import com.ticketsystem.common.error.ProblemKind;
import com.ticketsystem.support.TicketBuilder;
import com.ticketsystem.team.TeamFacade;
import com.ticketsystem.team.UserTeams;
import com.ticketsystem.ticket.TicketStatus;
import com.ticketsystem.ticket.TicketStatusChanged;
import com.ticketsystem.ticket.domain.Comment;
import com.ticketsystem.ticket.domain.CommentRepository;
import com.ticketsystem.ticket.domain.InvalidStatusTransitionException;
import com.ticketsystem.ticket.domain.Ticket;
import com.ticketsystem.ticket.domain.TicketCategory;
import com.ticketsystem.ticket.domain.TicketPage;
import com.ticketsystem.ticket.domain.TicketPriority;
import com.ticketsystem.ticket.domain.TicketRepository;
import com.ticketsystem.ticket.domain.TicketRoute;
import com.ticketsystem.ticket.domain.TicketRouteRepository;
import com.ticketsystem.ticket.domain.VisibilityScope;
import com.ticketsystem.user.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Os casos de uso de ticket, com repositorios, regra de acesso e publicador mockados.
 *
 * <p>A regra de acesso e mockada aqui porque tem teste proprio; o que se prova e que o
 * service a consulta antes de agir, e que uma recusa nao grava nem publica nada.
 */
@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    private static final Instant AGORA = Instant.parse("2026-09-24T12:00:00Z");
    private static final CurrentUser ANA = new CurrentUser(10L, UserRole.REQUESTER);
    private static final CurrentUser DIEGO = new CurrentUser(20L, UserRole.AGENT);

    @Mock
    private TicketRepository tickets;

    @Mock
    private CommentRepository comentarios;

    @Mock
    private TicketRouteRepository rotas;

    @Mock
    private TicketAccessPolicy acesso;

    @Mock
    private TeamFacade equipes;

    @Mock
    private ApplicationEventPublisher eventos;

    private TicketService service;
    private Ticket ticket;

    @BeforeEach
    void montar() {
        service = new TicketService(tickets, comentarios, rotas, acesso, equipes, eventos, Clock.fixed(AGORA, ZoneOffset.UTC));
        ticket = TicketBuilder.aTicket().requestedBy(ANA.id()).assignedToTeam(3L).build();
    }

    @Nested
    @DisplayName("abrir")
    class Abrir {

        @Test
        @DisplayName("o ticket vai para a equipe da rota da categoria, em nome de quem abriu")
        void abreNaEquipeDaRota() {
            when(rotas.findByCategory(TicketCategory.ACCESS)).thenReturn(Optional.of(new TicketRoute(TicketCategory.ACCESS, 8L)));
            when(tickets.save(any(Ticket.class))).thenAnswer(chamada -> chamada.getArgument(0));

            Ticket aberto = service.open(ANA, "Sem VPN", "Desde ontem.", TicketCategory.ACCESS, TicketPriority.HIGH);

            assertThat(aberto.getAssignedTeamId()).isEqualTo(8L);
            assertThat(aberto.getRequesterId()).isEqualTo(ANA.id());
            assertThat(aberto.getStatus()).isEqualTo(TicketStatus.OPEN);
        }

        @Test
        @DisplayName("categoria sem rota e 409, e nada e gravado")
        void categoriaSemRotaEh409() {
            // Sem rota nao ha para onde mandar. Um default esconderia a configuracao que
            // falta, e o ticket iria parar numa equipe que nao o espera.
            when(rotas.findByCategory(TicketCategory.HARDWARE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.open(ANA, "T", "D", TicketCategory.HARDWARE, TicketPriority.LOW))
                    .isInstanceOf(UnroutedCategoryException.class)
                    .satisfies(e -> assertThat(tipo(e)).isEqualTo(ProblemKind.CONFLICT));
            verify(tickets, never()).save(any());
        }
    }

    @Nested
    @DisplayName("listar")
    class Listar {

        private final TicketPage vazia = new TicketPage(List.of(), 0, 20, 0);

        @Test
        @DisplayName("admin lista sem filtro, e as equipes nem sao consultadas")
        void adminSemFiltro() {
            CurrentUser admin = new CurrentUser(1L, UserRole.ADMIN);
            when(tickets.findVisible(VisibilityScope.everything(1L), 0, 20)).thenReturn(vazia);

            assertThat(service.list(admin, 0, 20)).isSameAs(vazia);
            verify(equipes, never()).teamsOf(anyLong());
        }

        @Test
        @DisplayName("solicitante lista so os proprios, sem consultar equipe — nem um vinculo esquecido conta")
        void solicitanteSoOsProprios() {
            when(tickets.findVisible(VisibilityScope.ownTicketsOnly(ANA.id()), 1, 5)).thenReturn(vazia);

            assertThat(service.list(ANA, 1, 5)).isSameAs(vazia);
            verify(equipes, never()).teamsOf(anyLong());
        }

        @Test
        @DisplayName("agente lista com as equipes lidas agora, e nao do token")
        void agenteComAsEquipesDeAgora() {
            when(equipes.teamsOf(DIEGO.id())).thenReturn(new UserTeams(Set.of(3L, 4L), Set.of(4L)));
            when(tickets.findVisible(VisibilityScope.agent(DIEGO.id(), Set.of(3L, 4L), Set.of(4L)), 0, 20))
                    .thenReturn(vazia);

            assertThat(service.list(DIEGO, 0, 20)).isSameAs(vazia);
        }
    }

    @Nested
    @DisplayName("consultar")
    class Consultar {

        @Test
        @DisplayName("quem nao ve recebe 404, o mesmo de um ticket que nao existe")
        void quemNaoVeRecebe404() {
            when(tickets.findById(ticket.getId())).thenReturn(Optional.of(ticket));
            when(acesso.canView(DIEGO, ticket)).thenReturn(false);

            assertThatThrownBy(() -> service.findById(DIEGO, ticket.getId()))
                    .isInstanceOf(TicketNotFoundException.class)
                    .satisfies(e -> assertThat(tipo(e)).isEqualTo(ProblemKind.NOT_FOUND));
        }

        @Test
        @DisplayName("ticket que nao existe e 404")
        void inexistenteEh404() {
            when(tickets.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(DIEGO, 404L)).isInstanceOf(TicketNotFoundException.class);
        }

        @Test
        @DisplayName("quem ve recebe o ticket")
        void quemVeRecebe() {
            podeVer(ANA);

            assertThat(service.findById(ANA, ticket.getId())).isSameAs(ticket);
        }
    }

    @Nested
    @DisplayName("transicionar")
    class Transicionar {

        @Test
        @DisplayName("transicao permitida grava e publica o evento com de, para, quem e quando")
        void transicaoPublicaEvento() {
            podeVer(DIEGO);
            when(acesso.canTransition(DIEGO, ticket, TicketStatus.IN_PROGRESS)).thenReturn(true);
            when(tickets.save(ticket)).thenReturn(ticket);

            Ticket movido = service.transition(DIEGO, ticket.getId(), TicketStatus.IN_PROGRESS);

            assertThat(movido.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
            verify(eventos).publishEvent(new TicketStatusChanged(
                    ticket.getId(), TicketStatus.OPEN, TicketStatus.IN_PROGRESS, DIEGO.id(), AGORA));
        }

        @Test
        @DisplayName("sem permissao para a transicao e 403, sem gravar nem publicar")
        void semPermissaoEh403() {
            podeVer(ANA);
            when(acesso.canTransition(ANA, ticket, TicketStatus.IN_PROGRESS)).thenReturn(false);

            assertThatThrownBy(() -> service.transition(ANA, ticket.getId(), TicketStatus.IN_PROGRESS))
                    .isInstanceOf(TicketActionForbiddenException.class)
                    .satisfies(e -> assertThat(tipo(e)).isEqualTo(ProblemKind.FORBIDDEN));
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
            verify(tickets, never()).save(any());
            verifyNoInteractions(eventos);
        }

        @Test
        @DisplayName("transicao fora do fluxo e 409, sem gravar nem publicar")
        void foraDoFluxoEh409() {
            podeVer(DIEGO);
            when(acesso.canTransition(DIEGO, ticket, TicketStatus.CLOSED)).thenReturn(true);

            assertThatThrownBy(() -> service.transition(DIEGO, ticket.getId(), TicketStatus.CLOSED))
                    .isInstanceOf(InvalidStatusTransitionException.class);
            verify(tickets, never()).save(any());
            verifyNoInteractions(eventos);
        }

        @Test
        @DisplayName("quem nao ve o ticket recebe 404, e a permissao nem e consultada")
        void quemNaoVeRecebe404() {
            when(tickets.findById(ticket.getId())).thenReturn(Optional.of(ticket));
            when(acesso.canView(DIEGO, ticket)).thenReturn(false);

            assertThatThrownBy(() -> service.transition(DIEGO, ticket.getId(), TicketStatus.IN_PROGRESS))
                    .isInstanceOf(TicketNotFoundException.class);
            verify(acesso, never()).canTransition(any(), any(), any());
            verifyNoInteractions(eventos);
        }
    }

    @Nested
    @DisplayName("comentar")
    class Comentar {

        @Test
        @DisplayName("quem atende escreve nota interna")
        void quemAtendeEscreveNotaInterna() {
            podeVer(DIEGO);
            when(acesso.canHandle(DIEGO, ticket)).thenReturn(true);
            when(comentarios.save(any(Comment.class))).thenAnswer(chamada -> chamada.getArgument(0));

            Comment nota = service.comment(DIEGO, ticket.getId(), "Provavel DNS.", true);

            assertThat(nota.isInternal()).isTrue();
            assertThat(nota.getAuthorId()).isEqualTo(DIEGO.id());
            assertThat(nota.getTicketId()).isEqualTo(ticket.getId());
        }

        @Test
        @DisplayName("solicitante nao escreve nota interna: 403, e nada e gravado")
        void solicitanteNaoEscreveNotaInterna() {
            // Uma nota interna escrita pelo solicitante seria uma mensagem que ele mesmo nao
            // conseguiria ler depois — e que a equipe trataria como interna.
            podeVer(ANA);
            when(acesso.canHandle(ANA, ticket)).thenReturn(false);

            assertThatThrownBy(() -> service.comment(ANA, ticket.getId(), "Oi", true))
                    .isInstanceOf(TicketActionForbiddenException.class);
            verify(comentarios, never()).save(any());
        }

        @Test
        @DisplayName("solicitante responde publicamente")
        void solicitanteRespondePublicamente() {
            podeVer(ANA);
            when(comentarios.save(any(Comment.class))).thenAnswer(chamada -> chamada.getArgument(0));

            assertThat(service.comment(ANA, ticket.getId(), "Reiniciei.", false).isInternal()).isFalse();
        }

        @Test
        @DisplayName("quem nao ve o ticket nao comenta")
        void quemNaoVeNaoComenta() {
            when(tickets.findById(ticket.getId())).thenReturn(Optional.of(ticket));
            when(acesso.canView(DIEGO, ticket)).thenReturn(false);

            assertThatThrownBy(() -> service.comment(DIEGO, ticket.getId(), "x", false))
                    .isInstanceOf(TicketNotFoundException.class);
            verify(comentarios, never()).save(any());
        }
    }

    @Nested
    @DisplayName("ler a conversa")
    class LerConversa {

        @Test
        @DisplayName("quem nao atende recebe a consulta sem notas internas, filtrada no banco")
        void quemNaoAtendeRecebeSoOPublico() {
            podeVer(ANA);
            when(acesso.canHandle(ANA, ticket)).thenReturn(false);
            List<Comment> publicos = List.of(Comment.publicReply(ticket.getId(), DIEGO.id(), "Pode reiniciar?"));
            when(comentarios.findPublicByTicket(ticket.getId())).thenReturn(publicos);

            assertThat(service.comments(ANA, ticket.getId())).isEqualTo(publicos);
            // A consulta completa nem e feita: filtrar depois de carregar e o codigo em que um
            // refactor inocente passa a devolver tudo.
            verify(comentarios, never()).findByTicket(any());
        }

        @Test
        @DisplayName("quem atende recebe a conversa inteira")
        void quemAtendeRecebeTudo() {
            podeVer(DIEGO);
            when(acesso.canHandle(DIEGO, ticket)).thenReturn(true);
            List<Comment> todos = List.of(Comment.internalNote(ticket.getId(), DIEGO.id(), "DNS"));
            when(comentarios.findByTicket(ticket.getId())).thenReturn(todos);

            assertThat(service.comments(DIEGO, ticket.getId())).isEqualTo(todos);
            verify(comentarios, never()).findPublicByTicket(any());
        }
    }

    private void podeVer(CurrentUser ator) {
        when(tickets.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(acesso.canView(ator, ticket)).thenReturn(true);
    }

    private static ProblemKind tipo(Throwable excecao) {
        return ((DomainException) excecao).kind();
    }
}
