package com.ticketsystem.user.service;

import com.ticketsystem.common.error.DomainException;
import com.ticketsystem.common.error.ProblemKind;

/** Nao ha usuario com o id pedido. */
public class UserNotFoundException extends DomainException {

    public UserNotFoundException() {
        super(ProblemKind.NOT_FOUND, "Usuario nao encontrado", "Nao ha usuario com este id.");
    }
}
