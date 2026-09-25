package com.ticketsystem.ticket.domain;

import com.ticketsystem.common.domain.BaseEntity;
import com.ticketsystem.ticket.TicketStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * O chamado: o que foi pedido, por quem, em que status e com quem esta.
 *
 * <p><strong>Dois modos de atribuicao, sempre exatamente um</strong> (CLAUDE.md#atribuicao):
 * {@code assignedTeamId} preenchido e o modo equipe; {@code exclusiveAssigneeId}, o modo
 * exclusivo, com a equipe de origem preservada em {@code originTeamId}. A V5 garante isso
 * por CHECK; aqui, nenhuma operacao publica sai de um estado valido. Nesta fase o ticket so
 * nasce e vive em modo equipe — a troca de modo e da Fase 5.
 *
 * <p>Os ids de pessoa e de equipe sao {@code Long} puros, sem {@code @ManyToOne}: referenciar
 * classe interna de {@code user} ou {@code team} quebraria o build. A integridade e a das
 * chaves estrangeiras da V5.
 *
 * <p>{@code @Version} protege as transicoes. Duas pessoas movendo o mesmo ticket ao mesmo
 * tempo passariam ambas pela validacao de fluxo, e a segunda gravaria por cima da primeira.
 * Com a versao, a segunda recebe 409 e recarrega.
 */
@Entity
@Table(name = "tickets")
public class Ticket extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 10000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketCategory category;

    @Column(name = "requester_id", nullable = false, updatable = false)
    private Long requesterId;

    @Column(name = "assigned_team_id")
    private Long assignedTeamId;

    @Column(name = "current_assignee_id")
    private Long currentAssigneeId;

    @Column(name = "exclusive_assignee_id")
    private Long exclusiveAssigneeId;

    @Column(name = "origin_team_id")
    private Long originTeamId;

    @Version
    @Column(nullable = false)
    private Long version;

    /** Exigido pelo JPA. Nao use no codigo da aplicacao. */
    protected Ticket() {
    }

    private Ticket(Long requesterId, Long teamId, String title, String description,
            TicketCategory category, TicketPriority priority) {
        this.requesterId = requesterId;
        this.assignedTeamId = teamId;
        this.title = title;
        this.description = description;
        this.category = category;
        this.priority = priority;
        this.status = TicketStatus.OPEN;
    }

    /**
     * Um chamado novo: {@code OPEN}, em modo equipe, sem responsavel.
     *
     * <p>A equipe vem de fora — do roteamento da categoria —, e nunca e escolhida aqui.
     */
    public static Ticket open(Long requesterId, Long teamId, String title, String description,
            TicketCategory category, TicketPriority priority) {
        if (requesterId == null || teamId == null) {
            throw new IllegalArgumentException("ticket precisa de solicitante e de equipe");
        }
        if (category == null || priority == null) {
            throw new IllegalArgumentException("ticket precisa de categoria e de prioridade");
        }
        return new Ticket(requesterId, teamId, exigirTexto(title, "titulo"),
                exigirTexto(description, "descricao"), category, priority);
    }

    /**
     * Move o ticket para {@code destino} e devolve o status de onde ele saiu.
     *
     * <p>So o fluxo e conferido aqui. Quem pode pedir a transicao e decisao do
     * {@code TicketAccessPolicy}, que roda antes.
     */
    public TicketStatus transitionTo(TicketStatus destino) {
        if (!status.canTransitionTo(destino)) {
            throw new InvalidStatusTransitionException(status, destino);
        }
        TicketStatus anterior = status;
        this.status = destino;
        return anterior;
    }

    public boolean isInTeamMode() {
        return assignedTeamId != null;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public TicketCategory getCategory() {
        return category;
    }

    public Long getRequesterId() {
        return requesterId;
    }

    public Long getAssignedTeamId() {
        return assignedTeamId;
    }

    public Long getCurrentAssigneeId() {
        return currentAssigneeId;
    }

    public Long getExclusiveAssigneeId() {
        return exclusiveAssigneeId;
    }

    public Long getOriginTeamId() {
        return originTeamId;
    }

    private static String exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " do ticket e obrigatorio");
        }
        return valor.trim();
    }

    /** Sem titulo nem descricao: texto livre do solicitante nao vai para log. */
    @Override
    public String toString() {
        return "Ticket[id=%s, status=%s, teamId=%s]".formatted(getId(), status, assignedTeamId);
    }
}
