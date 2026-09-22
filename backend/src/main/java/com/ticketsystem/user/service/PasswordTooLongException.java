package com.ticketsystem.user.service;

import com.ticketsystem.common.error.DomainException;
import com.ticketsystem.common.error.ProblemKind;

/**
 * A senha passa do limite do BCrypt: 72 bytes.
 *
 * <p>O limite e em <em>bytes</em>, nao em caracteres, e por isso nao da para checa-lo so
 * com {@code @Size} no DTO: 40 letras acentuadas ja sao 80 bytes em UTF-8. Sem esta
 * checagem, o {@code BCryptPasswordEncoder} lancaria {@code IllegalArgumentException} e o
 * cliente receberia um erro interno por uma entrada que so estava longa demais.
 */
public class PasswordTooLongException extends DomainException {

    public PasswordTooLongException() {
        super(ProblemKind.INVALID, "Senha longa demais",
                "A senha pode ter no maximo 72 bytes. Letras acentuadas contam como dois.");
    }
}
