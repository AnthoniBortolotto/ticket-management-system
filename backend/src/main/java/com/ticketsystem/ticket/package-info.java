/**
 * Nucleo do dominio: ciclo de vida do chamado, conversa, roteamento e acesso.
 *
 * <p>A superficie publica e o que outros modulos vao consumir: {@code TicketStatus} e o
 * evento {@code TicketStatusChanged}, que {@code sla} e {@code audit} escutam. {@code domain},
 * {@code service}, {@code infra} e {@code web} sao internos e o Modulith quebra o build se
 * outro modulo os importar.
 *
 * <p>Depende de {@code team}, para perguntar se alguem participa da equipe do ticket, e de
 * {@code auth}, para saber quem pede. Nenhum dos dois sabe que tickets existem — e e isso que
 * mantem o grafo aciclico.
 *
 * <p>As entidades guardam ids puros de pessoa e de equipe, sem {@code @ManyToOne}:
 * referenciar classe interna de outro modulo quebraria o build.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Tickets")
package com.ticketsystem.ticket;
