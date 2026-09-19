/**
 * Configuracao transversal: seguranca, contrato OpenAPI e JPA.
 *
 * <p>Modulo aberto: qualquer modulo pode depender dele sem que o Modulith reclame.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        displayName = "Configuracao")
package com.ticketsystem.config;
