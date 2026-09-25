package com.ticketsystem.ticket.service;

import com.ticketsystem.common.error.DomainException;
import com.ticketsystem.common.error.ProblemKind;

/**
 * Nenhuma equipe atende a categoria escolhida.
 *
 * <p>E falta de configuracao, e nao erro de quem abre — mas o solicitante precisa saber que o
 * chamado nao foi registrado. 409 porque o pedido esta certo e e o estado do sistema que o
 * impede. Nao ha equipe default de proposito: ela esconderia a configuracao que falta.
 */
public class UnroutedCategoryException extends DomainException {

    public UnroutedCategoryException() {
        super(ProblemKind.CONFLICT, "Categoria sem equipe",
                "Nenhuma equipe atende esta categoria ainda. Avise um administrador.");
    }
}
