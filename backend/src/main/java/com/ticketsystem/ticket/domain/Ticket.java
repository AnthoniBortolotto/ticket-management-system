package com.ticketsystem.ticket.domain;

import com.ticketsystem.common.domain.BaseEntity;
import com.ticketsystem.ticket.TicketAssignment;
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
 * por CHECK; aqui, nenhuma operacao publica sai de um estado valido. As operacoes de
 * atribuicao conferem o <em>modo</em>; <em>quem</em> pode pedi-las, e se o destino e elegivel,
 * e decisao do service.
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

    /** Os quatro campos de atribuicao, como estao agora. */
    public TicketAssignment assignment() {
        return new TicketAssignment(assignedTeamId, currentAssigneeId, exclusiveAssigneeId, originTeamId);
    }

    /**
     * Marca quem esta atuando agora. O ticket continua da equipe, e qualquer membro pode tomar
     * o lugar — por isso nao ha checagem de "ja tem responsavel".
     */
    public void assignCurrent(Long userId) {
        exigirModoEquipe();
        this.currentAssigneeId = exigirId(userId, "responsavel atual");
    }

    /** Ninguem atuando: o ticket fica na equipe, esperando alguem assumir. */
    public void clearCurrent() {
        exigirModoEquipe();
        this.currentAssigneeId = null;
    }

    /**
     * Tira o ticket da equipe e o entrega a uma pessoa so. A equipe perde o acesso, mas fica
     * guardada como origem: o lider dela continua enxergando o ticket, e e para la que ele volta.
     */
    public void makeExclusive(Long assigneeId) {
        exigirModoEquipe();
        Long responsavel = exigirId(assigneeId, "responsavel exclusivo");
        this.originTeamId = assignedTeamId;
        this.assignedTeamId = null;
        this.currentAssigneeId = null;
        this.exclusiveAssigneeId = responsavel;
    }

    /** Devolve o ticket a equipe de origem, sem responsavel atual. */
    public void returnToTeam() {
        if (isInTeamMode()) {
            throw AssignmentConflictException.notInExclusiveMode();
        }
        this.assignedTeamId = originTeamId;
        this.originTeamId = null;
        this.exclusiveAssigneeId = null;
    }

    /**
     * Passa o ticket para outra equipe. O responsavel atual sai junto: ele e da equipe antiga,
     * e deixa-lo marcado apontaria para alguem que nem enxerga mais o ticket.
     */
    public void transferTo(Long teamId) {
        exigirModoEquipe();
        Long destino = exigirId(teamId, "equipe de destino");
        if (destino.equals(assignedTeamId)) {
            throw AssignmentConflictException.alreadyInTeam();
        }
        this.assignedTeamId = destino;
        this.currentAssigneeId = null;
    }

    private void exigirModoEquipe() {
        if (!isInTeamMode()) {
            throw AssignmentConflictException.notInTeamMode();
        }
    }

    private static Long exigirId(Long id, String campo) {
        if (id == null) {
            throw new IllegalArgumentException(campo + " e obrigatorio");
        }
        return id;
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
