package com.ticketsystem.ticket.domain;

/**
 * Sobre o que e o chamado. E o que o solicitante escolhe na abertura, e o que decide a
 * equipe que o recebe, pelo {@link TicketRoute}.
 *
 * <p>Valor novo aqui nao quebra contrato — entra na {@code v1} —, mas exige migration que
 * afrouxe os CHECKs {@code tickets_category_check} e {@code ticket_routes_category_check}
 * da V5, e uma rota configurada antes do primeiro ticket dele.
 */
public enum TicketCategory {
    HARDWARE,
    SOFTWARE,
    ACCESS,
    OTHER
}
