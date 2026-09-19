/**
 * Nucleo do dominio: ciclo de vida do chamado, atribuicao e visibilidade.
 *
 * <p>Modulo ainda sem implementacao. Ao criar a primeira classe aqui, anote este pacote
 * com {@code @ApplicationModule(displayName = "Tickets")}: a partir dai o Modulith passa a
 * tratar a raiz deste pacote como API publica e {@code domain}, {@code service},
 * {@code infra} e {@code web} como internos, quebrando o build se outro modulo os
 * importar.
 *
 * <p>Anotar um pacote vazio nao funciona — o ArchUnit falha ao refletir sobre um
 * {@code package-info} solitario.
 */
package com.ticketsystem.ticket;
