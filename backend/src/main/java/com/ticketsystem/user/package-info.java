/**
 * Usuarios e papeis globais. Dono da identidade e da credencial.
 *
 * <p>A superficie publica e {@code UserFacade}, {@code UserAccount} e {@code UserRole};
 * {@code domain}, {@code service}, {@code infra} e {@code web} sao internos e o Modulith
 * quebra o build se outro modulo os importar.
 *
 * <p><strong>O hash da senha nao sai daqui.</strong> Quem precisa conferir credencial
 * chama {@code UserFacade.authenticate}, que compara dentro do modulo e devolve so o
 * resultado. Uma consulta que devolvesse o hash seria um vazamento com fachada em volta.
 *
 * <p>{@code WorkSchedule} chega na Fase 6, junto com o relogio de SLA.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Usuarios")
package com.ticketsystem.user;
