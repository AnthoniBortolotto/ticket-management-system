package com.ticketsystem.ticket;

import java.time.Instant;

/**
 * Um ticket mudou de status. Publicado pelo {@code TicketService} na mesma transacao que
 * gravou a mudanca; o Modulith guarda a publicacao e reentrega se um listener falhar.
 *
 * <p>{@code sla} vai escutar para pausar e retomar o relogio, e {@code audit} para registrar
 * a transicao. {@code ticket} nao sabe que nenhum dos dois existe.
 *
 * <p><strong>So identificadores, nunca texto.</strong> O evento fica guardado, serializado,
 * em {@code event_publication_archive} — sem expurgo ainda (ver o cabecalho da V1). Titulo,
 * descricao ou nome de alguem aqui viraria dado pessoal armazenado por tempo indefinido fora
 * das tabelas de dominio.
 *
 * @param ticketId  o ticket
 * @param from      de onde saiu
 * @param to        para onde foi
 * @param changedBy quem pediu a transicao
 * @param changedAt quando, em UTC
 */
public record TicketStatusChanged(Long ticketId, TicketStatus from, TicketStatus to, Long changedBy, Instant changedAt) {
}
