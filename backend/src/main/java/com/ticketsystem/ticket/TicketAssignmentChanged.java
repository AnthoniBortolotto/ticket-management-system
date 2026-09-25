package com.ticketsystem.ticket;

import java.time.Instant;

/**
 * Um ticket mudou de maos: responsavel atual, equipe ou modo de atribuicao. Publicado na
 * mesma transacao que gravou a mudanca.
 *
 * <p>{@code audit} vai escutar para registrar a reatribuicao (CLAUDE.md#auditoria), e
 * {@code sla} para saber de quem e o horario de trabalho que vale.
 *
 * <p>Carrega o antes e o depois inteiros, e nao so o campo que mudou: quem le a auditoria
 * precisa entender a mudanca sem reconstruir o estado anterior.
 *
 * @param ticketId  o ticket
 * @param from      a atribuicao antes
 * @param to        a atribuicao depois
 * @param changedBy quem pediu — ou quem removeu alguem da equipe, quando a mudanca e
 *                  consequencia disso
 * @param changedAt quando, em UTC
 */
public record TicketAssignmentChanged(
        Long ticketId, TicketAssignment from, TicketAssignment to, Long changedBy, Instant changedAt) {
}
