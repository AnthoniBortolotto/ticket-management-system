package com.ticketsystem.support;

import com.ticketsystem.ticket.TicketStatus;
import com.ticketsystem.ticket.domain.Ticket;
import com.ticketsystem.ticket.domain.TicketCategory;
import com.ticketsystem.ticket.domain.TicketPriority;
import jakarta.persistence.EntityManager;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Monta tickets de teste em uma linha:
 *
 * <pre>{@code
 * TicketBuilder.aTicket().requestedBy(ana.getId()).assignedToTeam(suporte.getId()).build();
 * TicketBuilder.aTicket().withStatus(RESOLVED).persistIn(em);
 * }</pre>
 *
 * <p>Recebe ids, e nao entidades de {@code user} ou {@code team}: e o que o proprio
 * {@code Ticket} guarda, e e assim que o builder nao dissolve a fronteira entre modulos que o
 * Modulith nao confere em codigo de teste.
 *
 * <p>{@link #withStatus} e {@link #inExclusiveMode} escrevem o estado direto, por reflexao,
 * sem passar pelas operacoes da entidade. E de proposito: o teste de uma regra de acesso
 * sobre um ticket {@code RESOLVED} nao deve depender do caminho de transicoes ate la, e o
 * modo exclusivo ainda nao tem operacao — ela chega na Fase 5.
 */
public final class TicketBuilder {

    private static final AtomicLong SEQUENCIA = new AtomicLong(1_000);

    private Long requesterId = 1L;
    private Long teamId = 1L;
    private String title = "Ticket de teste";
    private String description = "Descricao de teste.";
    private TicketCategory category = TicketCategory.OTHER;
    private TicketPriority priority = TicketPriority.MEDIUM;
    private TicketStatus status = TicketStatus.OPEN;
    private Long exclusiveAssigneeId;
    private Long originTeamId;

    private TicketBuilder() {
    }

    public static TicketBuilder aTicket() {
        return new TicketBuilder();
    }

    public TicketBuilder requestedBy(Long requesterId) {
        this.requesterId = requesterId;
        return this;
    }

    public TicketBuilder assignedToTeam(Long teamId) {
        this.teamId = teamId;
        return this;
    }

    public TicketBuilder titled(String title) {
        this.title = title;
        return this;
    }

    public TicketBuilder withCategory(TicketCategory category) {
        this.category = category;
        return this;
    }

    public TicketBuilder withStatus(TicketStatus status) {
        this.status = status;
        return this;
    }

    /** Sai da equipe para um responsavel exclusivo, guardando a origem — como a V5 exige. */
    public TicketBuilder inExclusiveMode(Long assigneeId, Long originTeamId) {
        this.exclusiveAssigneeId = assigneeId;
        this.originTeamId = originTeamId;
        return this;
    }

    /** Em memoria, com id sintetico: sem id, {@code BaseEntity.equals} nunca iguala duas instancias. */
    public Ticket build() {
        Ticket ticket = montar();
        ReflectionTestUtils.setField(ticket, "id", SEQUENCIA.incrementAndGet());
        return ticket;
    }

    /** Grava de verdade. Exige transacao aberta e que solicitante e equipe existam. */
    public Ticket persistIn(EntityManager em) {
        Ticket ticket = montar();
        em.persist(ticket);
        em.flush();
        return ticket;
    }

    private Ticket montar() {
        Ticket ticket = Ticket.open(requesterId, teamId, title, description, category, priority);
        ReflectionTestUtils.setField(ticket, "status", status);
        if (exclusiveAssigneeId != null) {
            ReflectionTestUtils.setField(ticket, "assignedTeamId", null);
            ReflectionTestUtils.setField(ticket, "exclusiveAssigneeId", exclusiveAssigneeId);
            ReflectionTestUtils.setField(ticket, "originTeamId", originTeamId);
        }
        return ticket;
    }
}
