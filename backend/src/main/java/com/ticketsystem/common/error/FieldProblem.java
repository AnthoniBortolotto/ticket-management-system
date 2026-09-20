package com.ticketsystem.common.error;

import org.springframework.validation.FieldError;

/**
 * Um campo rejeitado pela validacao e o motivo.
 *
 * <p>Vai na propriedade {@code errors} do {@code ProblemDetail}: a RFC 9457 padroniza o
 * envelope, mas deixa os detalhes especificos do problema por conta da aplicacao, e
 * "quais campos falharam" e exatamente esse caso.
 */
public record FieldProblem(String field, String message) {

    static FieldProblem from(FieldError error) {
        String mensagem = error.getDefaultMessage() != null ? error.getDefaultMessage() : "valor invalido";
        return new FieldProblem(error.getField(), mensagem);
    }
}
