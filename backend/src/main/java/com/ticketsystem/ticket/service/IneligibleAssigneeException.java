package com.ticketsystem.ticket.service;

import com.ticketsystem.common.error.DomainException;
import com.ticketsystem.common.error.ProblemKind;

/**
 * O destino da atribuicao nao pode receber o ticket: nao participa da equipe dele, ou e
 * solicitante.
 *
 * <p>400: e um campo do pedido que esta errado. O {@code detail} nao diz qual das duas
 * condicoes falhou — responder "esta pessoa existe, mas e solicitante" revelaria o papel de
 * um id qualquer a quem so queria atribuir um ticket.
 */
public class IneligibleAssigneeException extends DomainException {

    public IneligibleAssigneeException() {
        super(ProblemKind.INVALID, "Responsavel invalido",
                "O responsavel precisa ser um agente ou admin que participe da equipe do ticket.");
    }
}
