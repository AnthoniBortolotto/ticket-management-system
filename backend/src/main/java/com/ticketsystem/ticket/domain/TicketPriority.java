package com.ticketsystem.ticket.domain;

/**
 * Prioridade do chamado. E a chave da politica de SLA — os prazos ficam no banco, em
 * {@code SlaPolicy}, e nunca aqui.
 *
 * <p>Em {@code domain} por enquanto, porque nenhum outro modulo a le ainda. Quando
 * {@code sla} precisar dela num evento ou na fachada, ela sobe para a raiz do modulo, como
 * o {@code TicketStatus}.
 *
 * <p>Os nomes casam com o CHECK {@code tickets_priority_check} da V5.
 */
public enum TicketPriority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT
}
