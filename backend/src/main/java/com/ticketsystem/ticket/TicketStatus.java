package com.ticketsystem.ticket;

import java.util.Set;

/**
 * Os status do chamado <strong>e</strong> as transicoes permitidas entre eles.
 *
 * <pre>
 * OPEN             -> IN_PROGRESS
 * IN_PROGRESS      -> WAITING_CUSTOMER, RESOLVED
 * WAITING_CUSTOMER -> IN_PROGRESS, RESOLVED
 * RESOLVED         -> CLOSED, REOPENED
 * CLOSED           -> REOPENED
 * REOPENED         -> IN_PROGRESS
 * </pre>
 *
 * <p>E o diagrama do README mais um atalho decidido na Fase 4: {@code IN_PROGRESS} resolve
 * direto, sem precisar passar por {@code WAITING_CUSTOMER}. Nenhum status volta para
 * {@code OPEN} — ticket que ja foi atendido e volta e {@code REOPENED} — e nenhum
 * transiciona para si mesmo.
 *
 * <p>Fica na raiz do modulo, e nao em {@code ticket.domain}, pelo mesmo motivo do
 * {@code UserRole}: viaja dentro de {@link TicketStatusChanged}, que {@code sla} e
 * {@code audit} vao consumir. Tipo em subpacote e interno, e o Modulith quebraria o build
 * no primeiro listener.
 *
 * <p>Os nomes casam com o CHECK {@code tickets_status_check} da V5.
 *
 * <p><em>Quem</em> pode pedir cada transicao nao mora aqui: e regra de acesso, do
 * {@code TicketAccessPolicy}. Aqui mora so o que e possivel.
 */
public enum TicketStatus {

    /** Aberto e ainda nao atendido. */
    OPEN,

    /** Alguem da equipe esta atuando. */
    IN_PROGRESS,

    /** Parado a espera do solicitante. O relogio do SLA pausa aqui. */
    WAITING_CUSTOMER,

    /** Quem atende considera resolvido; o solicitante confirma ou reabre. */
    RESOLVED,

    /** Encerrado. So sai daqui reabrindo. */
    CLOSED,

    /** Voltou depois de resolvido ou fechado. */
    REOPENED;

    /**
     * Um {@code switch} sobre o proprio enum, e nao um mapa montado no construtor: constante
     * de enum nao pode referenciar outra declarada depois dela, e o compilador exige que todo
     * status tenha um caso aqui.
     */
    private Set<TicketStatus> proximos() {
        return switch (this) {
            case OPEN -> Set.of(IN_PROGRESS);
            case IN_PROGRESS -> Set.of(WAITING_CUSTOMER, RESOLVED);
            case WAITING_CUSTOMER -> Set.of(IN_PROGRESS, RESOLVED);
            case RESOLVED -> Set.of(CLOSED, REOPENED);
            case CLOSED -> Set.of(REOPENED);
            case REOPENED -> Set.of(IN_PROGRESS);
        };
    }

    public boolean canTransitionTo(TicketStatus destino) {
        return destino != null && proximos().contains(destino);
    }
}
