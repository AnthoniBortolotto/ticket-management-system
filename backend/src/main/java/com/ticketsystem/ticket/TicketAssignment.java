package com.ticketsystem.ticket;

/**
 * Com quem o ticket esta: a fotografia dos quatro campos de atribuicao.
 *
 * <p>Em modo equipe, {@code teamId} preenchido e {@code currentAssigneeId} opcional; em modo
 * exclusivo, {@code exclusiveAssigneeId} e {@code originTeamId}. Nunca os dois — e a V5 que
 * garante.
 *
 * <p>Na raiz do modulo porque viaja dentro de {@link TicketAssignmentChanged}, que
 * {@code audit} vai consumir.
 */
public record TicketAssignment(Long teamId, Long currentAssigneeId, Long exclusiveAssigneeId, Long originTeamId) {
}
