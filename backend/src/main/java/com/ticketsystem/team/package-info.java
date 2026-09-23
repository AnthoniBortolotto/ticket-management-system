/**
 * Equipes e vinculos de membro. Responde quem participa e quem lidera cada equipe.
 *
 * <p>A superficie publica e {@code TeamFacade}; {@code domain}, {@code service},
 * {@code infra} e {@code web} sao internos e o Modulith quebra o build se outro modulo os
 * importar.
 *
 * <p>Depende de {@code user}, para saber se quem vai entrar numa equipe existe e que papel
 * tem, e de {@code auth}, para saber quem esta pedindo. Nenhum dos dois depende daqui — o
 * grafo continua aciclico.
 *
 * <p>As entidades guardam {@code Long userId} puro, sem {@code @ManyToOne User}:
 * referenciar classe interna de outro modulo quebraria o build. A cascata ao apagar o
 * usuario e a do banco.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Equipes")
package com.ticketsystem.team;
