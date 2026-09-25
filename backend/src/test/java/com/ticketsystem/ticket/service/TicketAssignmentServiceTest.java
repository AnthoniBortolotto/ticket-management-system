package com.ticketsystem.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ticketsystem.auth.CurrentUser;
import com.ticketsystem.common.error.DomainException;
import com.ticketsystem.common.error.ProblemKind;
import com.ticketsystem.support.TicketBuilder;
import com.ticketsystem.team.TeamFacade;
import com.ticketsystem.ticket.TicketAssignment;
import com.ticketsystem.ticket.TicketAssignmentChanged;
import com.ticketsystem.ticket.domain.AssignmentConflictException;
import com.ticketsystem.ticket.domain.Ticket;
import com.ticketsystem.ticket.domain.TicketRepository;
import com.ticketsystem.user.UserAccount;
import com.ticketsystem.user.UserFacade;
import com.ticketsystem.user.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Reatribuicao, com repositorio, regra de acesso e fachadas mockadas.
 *
 * <p>A regra de <em>quem</em> pode e do {@code TicketAccessPolicy}, que tem teste proprio; o
 * modo e do {@code Ticket}. O que se prova aqui e a ordem — 404, 403, 409, 400 —, que o
 * destino precisa ser da equipe, e que so mudanca de verdade grava e publica.
 */
@ExtendWith(MockitoExtension.class)
class TicketAssignmentServiceTest {

    private static final Instant AGORA = Instant.parse("2026-09-25T12:00:00Z");
    private static final Long EQUIPE = 3L;
    private static final Long OUTRA_EQUIPE = 4L;
    private static final CurrentUser CARLA = new CurrentUser(30L, UserRole.AGENT);
    private static final Long DIEGO = 20L;
    private static final Long MARINA = 21L;

    @Mock
    private TicketRepository tickets;

    @Mock
    private TicketAccessPolicy acesso;

    @Mock
    private TeamFacade equipes;

    @Mock
    private UserFacade usuarios;

    @Mock
    private ApplicationEventPublisher eventos;

    private TicketAssignmentService service;
    private Ticket ticket;

    @BeforeEach
    void montar() {
        service = new TicketAssignmentService(
                tickets, acesso, equipes, usuarios, eventos, Clock.fixed(AGORA, ZoneOffset.UTC));
        ticket = TicketBuilder.aTicket().assignedToTeam(EQUIPE).build();
        lenient().when(tickets.save(any(Ticket.class))).thenAnswer(chamada -> chamada.getArgument(0));
    }

    @Nested
    @DisplayName("responsavel atual")
    class ResponsavelAtual {

        @Test
        @DisplayName("quem atende designa um membro da equipe, e a mudanca e publicada com o antes e o depois")
        void designaMembro() {
            podeVer();
            when(acesso.canHandle(CARLA, ticket)).thenReturn(true);
            eMembro(EQUIPE, DIEGO, UserRole.AGENT);

            // O retorno e o que o controller responde; sem afirmar sobre ele, um `return null`
            // passaria — foi o PITest que apontou.
            assertThat(service.assignCurrent(CARLA, ticket.getId(), DIEGO)).isSameAs(ticket);

            assertThat(ticket.getCurrentAssigneeId()).isEqualTo(DIEGO);
            verify(eventos).publishEvent(new TicketAssignmentChanged(ticket.getId(),
                    new TicketAssignment(EQUIPE, null, null, null),
                    new TicketAssignment(EQUIPE, DIEGO, null, null), CARLA.id(), AGORA));
        }

        @Test
        @DisplayName("quem nao atende: 403, sem gravar nem publicar")
        void quemNaoAtendeEh403() {
            podeVer();
            when(acesso.canHandle(CARLA, ticket)).thenReturn(false);

            assertThatThrownBy(() -> service.assignCurrent(CARLA, ticket.getId(), DIEGO))
                    .isInstanceOf(TicketActionForbiddenException.class)
                    .satisfies(e -> assertThat(tipo(e)).isEqualTo(ProblemKind.FORBIDDEN));
            verify(tickets, never()).save(any());
            verifyNoInteractions(eventos);
        }

        @Test
        @DisplayName("destino fora da equipe: 400, e nem se pergunta quem ele e")
        void destinoForaDaEquipeEh400() {
            podeVer();
            when(acesso.canHandle(CARLA, ticket)).thenReturn(true);
            when(equipes.isMember(EQUIPE, MARINA)).thenReturn(false);

            assertThatThrownBy(() -> service.assignCurrent(CARLA, ticket.getId(), MARINA))
                    .isInstanceOf(IneligibleAssigneeException.class)
                    .satisfies(e -> assertThat(tipo(e)).isEqualTo(ProblemKind.INVALID));
            verify(usuarios, never()).findById(anyLong());
            verifyNoInteractions(eventos);
        }

        @Test
        @DisplayName("solicitante com vinculo esquecido nao vira responsavel: 400")
        void solicitanteNaoEhResponsavel() {
            podeVer();
            when(acesso.canHandle(CARLA, ticket)).thenReturn(true);
            eMembro(EQUIPE, MARINA, UserRole.REQUESTER);

            assertThatThrownBy(() -> service.assignCurrent(CARLA, ticket.getId(), MARINA))
                    .isInstanceOf(IneligibleAssigneeException.class);
            verifyNoInteractions(eventos);
        }

        @Test
        @DisplayName("designar quem ja e o responsavel nao grava nem publica")
        void semMudancaSemEvento() {
            // Um evento sem mudanca viraria uma linha de auditoria dizendo que algo aconteceu.
            ticket.assignCurrent(DIEGO);
            podeVer();
            when(acesso.canHandle(CARLA, ticket)).thenReturn(true);
            eMembro(EQUIPE, DIEGO, UserRole.AGENT);

            assertThat(service.assignCurrent(CARLA, ticket.getId(), DIEGO)).isSameAs(ticket);

            verify(tickets, never()).save(any());
            verifyNoInteractions(eventos);
        }

        @Test
        @DisplayName("em modo exclusivo: 409 antes de olhar o destino")
        void emModoExclusivoEh409() {
            Ticket exclusivo = TicketBuilder.aTicket().inExclusiveMode(DIEGO, EQUIPE).build();
            when(tickets.findById(exclusivo.getId())).thenReturn(Optional.of(exclusivo));
            when(acesso.canView(CARLA, exclusivo)).thenReturn(true);
            when(acesso.canHandle(CARLA, exclusivo)).thenReturn(true);

            assertThatThrownBy(() -> service.assignCurrent(CARLA, exclusivo.getId(), MARINA))
                    .isInstanceOf(AssignmentConflictException.class);
            verifyNoInteractions(equipes, eventos);
        }

        @Test
        @DisplayName("largar o ticket publica a mudanca; largar o que ja esta sem ninguem, nao")
        void largar() {
            ticket.assignCurrent(DIEGO);
            podeVer();
            when(acesso.canHandle(CARLA, ticket)).thenReturn(true);

            assertThat(service.clearCurrent(CARLA, ticket.getId())).isSameAs(ticket);
            assertThat(service.clearCurrent(CARLA, ticket.getId())).isSameAs(ticket);

            assertThat(ticket.getCurrentAssigneeId()).isNull();
            verify(eventos).publishEvent(any(TicketAssignmentChanged.class));
        }
    }

    @Nested
    @DisplayName("modo exclusivo")
    class ModoExclusivo {

        @Test
        @DisplayName("quem despacha manda para um membro da equipe; a equipe vira origem")
        void mandaParaExclusivo() {
            podeVer();
            when(acesso.canDispatch(CARLA, ticket)).thenReturn(true);
            eMembro(EQUIPE, DIEGO, UserRole.AGENT);

            assertThat(service.makeExclusive(CARLA, ticket.getId(), DIEGO)).isSameAs(ticket);

            assertThat(ticket.assignment()).isEqualTo(new TicketAssignment(null, null, DIEGO, EQUIPE));
            verify(eventos).publishEvent(any(TicketAssignmentChanged.class));
        }

        @Test
        @DisplayName("quem nao despacha: 403")
        void quemNaoDespachaEh403() {
            podeVer();
            when(acesso.canDispatch(CARLA, ticket)).thenReturn(false);

            assertThatThrownBy(() -> service.makeExclusive(CARLA, ticket.getId(), DIEGO))
                    .isInstanceOf(TicketActionForbiddenException.class);
            assertThat(ticket.isInTeamMode()).isTrue();
            verifyNoInteractions(eventos);
        }

        @Test
        @DisplayName("destino fora da equipe: 400")
        void destinoForaDaEquipeEh400() {
            podeVer();
            when(acesso.canDispatch(CARLA, ticket)).thenReturn(true);
            when(equipes.isMember(EQUIPE, MARINA)).thenReturn(false);

            assertThatThrownBy(() -> service.makeExclusive(CARLA, ticket.getId(), MARINA))
                    .isInstanceOf(IneligibleAssigneeException.class);
            assertThat(ticket.isInTeamMode()).isTrue();
        }

        @Test
        @DisplayName("quem atende o exclusivo devolve para a origem")
        void devolve() {
            Ticket exclusivo = TicketBuilder.aTicket().inExclusiveMode(DIEGO, EQUIPE).build();
            when(tickets.findById(exclusivo.getId())).thenReturn(Optional.of(exclusivo));
            when(acesso.canView(CARLA, exclusivo)).thenReturn(true);
            when(acesso.canHandle(CARLA, exclusivo)).thenReturn(true);

            assertThat(service.returnToTeam(CARLA, exclusivo.getId())).isSameAs(exclusivo);

            assertThat(exclusivo.assignment()).isEqualTo(new TicketAssignment(EQUIPE, null, null, null));
            verify(eventos).publishEvent(any(TicketAssignmentChanged.class));
        }

        @Test
        @DisplayName("quem nao atende nao devolve: 403")
        void quemNaoAtendeNaoDevolve() {
            Ticket exclusivo = TicketBuilder.aTicket().inExclusiveMode(DIEGO, EQUIPE).build();
            when(tickets.findById(exclusivo.getId())).thenReturn(Optional.of(exclusivo));
            when(acesso.canView(CARLA, exclusivo)).thenReturn(true);
            when(acesso.canHandle(CARLA, exclusivo)).thenReturn(false);

            assertThatThrownBy(() -> service.returnToTeam(CARLA, exclusivo.getId()))
                    .isInstanceOf(TicketActionForbiddenException.class);
            assertThat(exclusivo.isInTeamMode()).isFalse();
        }
    }

    @Nested
    @DisplayName("transferencia")
    class Transferencia {

        @Test
        @DisplayName("quem despacha transfere para outra equipe")
        void transfere() {
            podeVer();
            when(acesso.canDispatch(CARLA, ticket)).thenReturn(true);
            when(equipes.exists(OUTRA_EQUIPE)).thenReturn(true);

            assertThat(service.transfer(CARLA, ticket.getId(), OUTRA_EQUIPE)).isSameAs(ticket);

            assertThat(ticket.getAssignedTeamId()).isEqualTo(OUTRA_EQUIPE);
            verify(eventos).publishEvent(any(TicketAssignmentChanged.class));
        }

        @Test
        @DisplayName("equipe que nao existe: 400")
        void equipeInexistenteEh400() {
            podeVer();
            when(acesso.canDispatch(CARLA, ticket)).thenReturn(true);
            when(equipes.exists(99L)).thenReturn(false);

            assertThatThrownBy(() -> service.transfer(CARLA, ticket.getId(), 99L))
                    .isInstanceOf(UnknownTeamException.class);
            assertThat(ticket.getAssignedTeamId()).isEqualTo(EQUIPE);
        }

        @Test
        @DisplayName("quem nao despacha: 403, e nem se pergunta se a equipe existe")
        void quemNaoDespachaEh403() {
            podeVer();
            when(acesso.canDispatch(CARLA, ticket)).thenReturn(false);

            assertThatThrownBy(() -> service.transfer(CARLA, ticket.getId(), OUTRA_EQUIPE))
                    .isInstanceOf(TicketActionForbiddenException.class);
            verify(equipes, never()).exists(anyLong());
        }
    }

    @Test
    @DisplayName("quem nao ve o ticket recebe 404 em qualquer operacao, e a permissao nem e consultada")
    void quemNaoVeRecebe404() {
        when(tickets.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(acesso.canView(CARLA, ticket)).thenReturn(false);

        assertThatThrownBy(() -> service.makeExclusive(CARLA, ticket.getId(), DIEGO))
                .isInstanceOf(TicketNotFoundException.class);
        verify(acesso, never()).canDispatch(any(), any());
    }

    @Test
    @DisplayName("quem sai da equipe deixa de ser o responsavel atual dos tickets dela")
    void saidaDaEquipeLimpaOResponsavel() {
        Ticket dele = TicketBuilder.aTicket().assignedToTeam(EQUIPE).build();
        dele.assignCurrent(DIEGO);
        when(tickets.findInTeamWithCurrentAssignee(EQUIPE, DIEGO)).thenReturn(List.of(dele));

        service.releaseCurrentAssignee(EQUIPE, DIEGO, CARLA.id());

        assertThat(dele.getCurrentAssigneeId()).isNull();
        // Quem removeu a pessoa da equipe e quem aparece na auditoria da consequencia.
        verify(eventos).publishEvent(new TicketAssignmentChanged(dele.getId(),
                new TicketAssignment(EQUIPE, DIEGO, null, null),
                new TicketAssignment(EQUIPE, null, null, null), CARLA.id(), AGORA));
    }

    private void podeVer() {
        when(tickets.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(acesso.canView(CARLA, ticket)).thenReturn(true);
    }

    private void eMembro(Long equipe, Long pessoa, UserRole papel) {
        when(equipes.isMember(equipe, pessoa)).thenReturn(true);
        when(usuarios.findById(pessoa)).thenReturn(new UserAccount(pessoa, "p" + pessoa + "@x.com", "Pessoa", papel));
    }

    private static ProblemKind tipo(Throwable excecao) {
        return ((DomainException) excecao).kind();
    }
}
