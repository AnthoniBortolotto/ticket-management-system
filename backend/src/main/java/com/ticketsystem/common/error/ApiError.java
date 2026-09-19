package com.ticketsystem.common.error;

import java.time.Instant;
import java.util.List;

/**
 * Corpo unico de erro da API. Toda resposta 4xx e 5xx sai neste formato, para que o
 * frontend tenha um so caminho de tratamento.
 *
 * @param fields preenchido apenas em erro de validacao; vazio nos demais casos
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldProblem> fields) {

    /** Um campo rejeitado pela validacao e o motivo. */
    public record FieldProblem(String field, String message) {
    }

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, List.of());
    }
}
