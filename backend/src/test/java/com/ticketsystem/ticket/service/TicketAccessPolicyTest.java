package com.ticketsystem.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ticketsystem.auth.CurrentUser;
import com.ticketsystem.support.TicketBuilder;
import com.ticketsystem.team.TeamFacade;
import com.ticketsystem.ticket.TicketStatus;
import com.ticketsystem.ticket.domain.Ticket;
import com.ticketsystem.user.UserRole;
import java.util.EnumSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * A regra de acesso a um ticket ja carregado, uma linha da tabela do CLAUDE.md por teste.
 *
 * <p>Os casos negativos vem primeiro de proposito: e o lado em que um erro vira vazamento.
 * As assercoes saem da tabela de visibilidade e da decisao da Fase 4 sobre o solicitante,
 * nao do codigo.
 *
 * <p>Em modo exclusivo a equipe de origem perde o acesso; ficam o responsavel exclusivo e o
 * lider da origem, que alem de ver tambem atua (decisao da Fase 5). O
 * {@code TicketSpecificationsIT} confere que a listagem em SQL concorda com cada linha daqui.
 */
@ExtendWith(MockitoExtension.class)
class TicketAccessPolicyTest {

    private static final Long EQUIPE = 3L;
    private static final Long OUTRA_EQUIPE = 4L;

    private static final CurrentUser SOLICITANTE = new CurrentUser(10L, UserRole.REQUESTER);
    private static final CurrentUser OUTRO_SOLICITANTE = new CurrentUser(11L, UserRole.REQUESTER);
    private static final CurrentUser MEMBRO = new CurrentUser(20L, UserRole.AGENT);
    private static final CurrentUser DE_OUTRA_EQUIPE = new CurrentUser(21L, UserRole.AGENT);
    private static final CurrentUser ADMIN = new CurrentUser(1L, UserRole.ADMIN);
    private static final CurrentUser LIDER = new CurrentUser(30L, UserRole.AGENT);
    private static final CurrentUser RESPONSAVEL_EXCLUSIVO = new CurrentUser(31L, UserRole.AGENT);

    @Mock
    private TeamFacade equipes;

    @InjectMocks
    private TicketAccessPolicy acesso;

    private Ticket ticket;

    @BeforeEach
    void montar() {
        ticket = TicketBuilder.aTicket().requestedBy(SOLICITANTE.id()).assignedToTeam(EQUIPE).build();
        // lenient: nem todo teste chega a perguntar a equipe — e varios afirmam justamente
        // que ela nao foi consultada.
        lenient().when(equipes.isMember(EQUIPE, MEMBRO.id())).thenReturn(true);
        lenient().when(equipes.isMember(OUTRA_EQUIPE, DE_OUTRA_EQUIPE.id())).thenReturn(true);
        lenient().when(equipes.isMember(EQUIPE, LIDER.id())).thenReturn(true);
        lenient().when(equipes.isLead(EQUIPE, LIDER.id())).thenReturn(true);
        lenient().when(equipes.isLead(OUTRA_EQUIPE, DE_OUTRA_EQUIPE.id())).thenReturn(true);
    }

    // --- quem NAO ve ----------------------------------------------------------------------

    @Test
    @DisplayName("agente de outra equipe nao ve o ticket")
    void agenteDeOutraEquipeNaoVe() {
        assertThat(acesso.canView(DE_OUTRA_EQUIPE, ticket)).isFalse();
        assertThat(acesso.canHandle(DE_OUTRA_EQUIPE, ticket)).isFalse();
    }

    @Test
    @DisplayName("outro solicitante nao ve o ticket")
    void outroSolicitanteNaoVe() {
        assertThat(acesso.canView(OUTRO_SOLICITANTE, ticket)).isFalse();
    }

    @Test
    @DisplayName("em modo exclusivo, a equipe de origem perde o acesso")
    void equipeDeOrigemPerdeOAcesso() {
        Ticket exclusivo = TicketBuilder.aTicket().requestedBy(SOLICITANTE.id())
                .inExclusiveMode(99L, EQUIPE).build();

        assertThat(acesso.canView(MEMBRO, exclusivo)).isFalse();
        assertThat(acesso.canHandle(MEMBRO, exclusivo)).isFalse();
    }

    @Test
    @DisplayName("solicitante nunca atende o proprio ticket, entao nunca ve nota interna")
    void solicitanteNaoAtende() {
        assertThat(acesso.canHandle(SOLICITANTE, ticket)).isFalse();
        // Nem pergunta a equipe: papel REQUESTER basta para responder.
        verify(equipes, never()).isMember(anyLong(), anyLong());
    }

    @Test
    @DisplayName("solicitante com vinculo de equipe esquecido continua sem atender")
    void solicitanteComVinculoAntigoNaoAtende() {
        // Defesa em profundidade: a Fase 3 impede solicitante de entrar em equipe, mas um
        // agente rebaixado a solicitante manteria o vinculo antigo. Sem esta regra, ele
        // passaria a ler as notas internas dos tickets da equipe.
        lenient().when(equipes.isMember(EQUIPE, OUTRO_SOLICITANTE.id())).thenReturn(true);

        assertThat(acesso.canHandle(OUTRO_SOLICITANTE, ticket)).isFalse();
        assertThat(acesso.canView(OUTRO_SOLICITANTE, ticket)).isFalse();
    }

    @ParameterizedTest(name = "solicitante nao move para {0}")
    @EnumSource(value = TicketStatus.class, names = {"CLOSED", "REOPENED"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("solicitante so decide fechar ou reabrir")
    void solicitanteNaoMoveOFluxoDoAtendimento(TicketStatus destino) {
        assertThat(acesso.canTransition(SOLICITANTE, ticket, destino)).isFalse();
    }

    @Test
    @DisplayName("quem nao ve o ticket nao move o ticket, nem para fechar")
    void quemNaoVeNaoMove() {
        assertThat(acesso.canTransition(OUTRO_SOLICITANTE, ticket, TicketStatus.CLOSED)).isFalse();
        assertThat(acesso.canTransition(DE_OUTRA_EQUIPE, ticket, TicketStatus.IN_PROGRESS)).isFalse();
    }

    // --- quem ve --------------------------------------------------------------------------

    @Test
    @DisplayName("solicitante sempre ve o proprio ticket, em qualquer modo")
    void solicitanteVeOProprio() {
        Ticket exclusivo = TicketBuilder.aTicket().requestedBy(SOLICITANTE.id())
                .inExclusiveMode(99L, EQUIPE).build();

        assertThat(acesso.canView(SOLICITANTE, ticket)).isTrue();
        assertThat(acesso.canView(SOLICITANTE, exclusivo)).isTrue();
    }

    @ParameterizedTest(name = "solicitante move para {0}")
    @EnumSource(value = TicketStatus.class, names = {"CLOSED", "REOPENED"})
    @DisplayName("solicitante confirma o fechamento e reabre")
    void solicitanteFechaEReabre(TicketStatus destino) {
        // Se a partir do status atual isso e possivel e o fluxo que decide, no TicketStatus.
        // Aqui so se decide que a decisao e dele.
        assertThat(acesso.canTransition(SOLICITANTE, ticket, destino)).isTrue();
    }

    @Test
    @DisplayName("membro da equipe ve, atende e move o ticket para qualquer status")
    void membroAtende() {
        assertThat(acesso.canView(MEMBRO, ticket)).isTrue();
        assertThat(acesso.canHandle(MEMBRO, ticket)).isTrue();
        for (TicketStatus destino : EnumSet.allOf(TicketStatus.class)) {
            assertThat(acesso.canTransition(MEMBRO, ticket, destino)).as(destino.name()).isTrue();
        }
    }

    @Test
    @DisplayName("admin ve e atende qualquer ticket, sem participar de equipe nenhuma")
    void adminAtendeTudo() {
        Ticket exclusivo = TicketBuilder.aTicket().inExclusiveMode(99L, EQUIPE).build();

        assertThat(acesso.canView(ADMIN, ticket)).isTrue();
        assertThat(acesso.canHandle(ADMIN, exclusivo)).isTrue();
        assertThat(acesso.canTransition(ADMIN, ticket, TicketStatus.IN_PROGRESS)).isTrue();
        verify(equipes, never()).isMember(anyLong(), anyLong());
    }

    @Test
    @DisplayName("agente que abriu ticket para outra equipe ve como solicitante, e so")
    void agenteSolicitante() {
        // Um agente tambem abre chamado. Fora da equipe que o recebeu, ele e so o
        // solicitante: ve, fecha e reabre, mas nao le nota interna.
        Ticket doAgente = TicketBuilder.aTicket().requestedBy(DE_OUTRA_EQUIPE.id()).assignedToTeam(EQUIPE).build();

        assertThat(acesso.canView(DE_OUTRA_EQUIPE, doAgente)).isTrue();
        assertThat(acesso.canHandle(DE_OUTRA_EQUIPE, doAgente)).isFalse();
        assertThat(acesso.canTransition(DE_OUTRA_EQUIPE, doAgente, TicketStatus.REOPENED)).isTrue();
        assertThat(acesso.canTransition(DE_OUTRA_EQUIPE, doAgente, TicketStatus.IN_PROGRESS)).isFalse();
    }

    // --- modo exclusivo -------------------------------------------------------------------

    @Test
    @DisplayName("em modo exclusivo, o responsavel ve e atua — e so no ticket dele")
    void responsavelExclusivoAtua() {
        Ticket dele = exclusivoDe(RESPONSAVEL_EXCLUSIVO);
        Ticket deOutro = exclusivoDe(new CurrentUser(99L, UserRole.AGENT));

        assertThat(acesso.canView(RESPONSAVEL_EXCLUSIVO, dele)).isTrue();
        assertThat(acesso.canHandle(RESPONSAVEL_EXCLUSIVO, dele)).isTrue();
        assertThat(acesso.canView(RESPONSAVEL_EXCLUSIVO, deOutro)).isFalse();
    }

    @Test
    @DisplayName("em modo exclusivo, o lider da origem ve e atua; o lider de outra equipe, nao")
    void liderDaOrigemAtua() {
        Ticket exclusivo = exclusivoDe(RESPONSAVEL_EXCLUSIVO);

        assertThat(acesso.canView(LIDER, exclusivo)).isTrue();
        assertThat(acesso.canHandle(LIDER, exclusivo)).isTrue();
        // DE_OUTRA_EQUIPE lidera OUTRA_EQUIPE: liderar nao e papel global.
        assertThat(acesso.canView(DE_OUTRA_EQUIPE, exclusivo)).isFalse();
    }

    // --- quem decide para onde o ticket vai -----------------------------------------------

    @Test
    @DisplayName("so o lider da equipe atual e o admin mandam o ticket para outro lugar")
    void soLiderEAdminDespacham() {
        assertThat(acesso.canDispatch(LIDER, ticket)).isTrue();
        assertThat(acesso.canDispatch(ADMIN, ticket)).isTrue();
        assertThat(acesso.canDispatch(MEMBRO, ticket)).isFalse();
        assertThat(acesso.canDispatch(DE_OUTRA_EQUIPE, ticket)).isFalse();
        assertThat(acesso.canDispatch(SOLICITANTE, ticket)).isFalse();
    }

    @Test
    @DisplayName("ticket exclusivo nao se despacha de novo, nem pelo lider da origem: devolve antes")
    void exclusivoNaoSeDespacha() {
        // Admin despacha qualquer ticket; se o modo permite, e o dominio que diz.
        assertThat(acesso.canDispatch(LIDER, exclusivoDe(RESPONSAVEL_EXCLUSIVO))).isFalse();
    }

    private Ticket exclusivoDe(CurrentUser responsavel) {
        return TicketBuilder.aTicket().requestedBy(SOLICITANTE.id()).inExclusiveMode(responsavel.id(), EQUIPE).build();
    }
}
