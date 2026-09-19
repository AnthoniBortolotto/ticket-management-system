package com.ticketsystem.common.error;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Comparator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Unico ponto que traduz excecao em resposta HTTP.
 *
 * <p>Controller nao monta {@code ResponseEntity} com status na mao: ele lanca excecao de
 * dominio e o mapeamento para status vive aqui. Cada modulo novo registra aqui as suas
 * excecoes, e so aqui.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    /** Bean Validation falhou em um DTO de entrada. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> onValidationFailure(
            MethodArgumentNotValidException exception, HttpServletRequest request) {

        List<ApiError.FieldProblem> fields = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new ApiError.FieldProblem(error.getField(), messageOf(error)))
            .sorted(Comparator.comparing(ApiError.FieldProblem::field))
            .toList();

        ApiError body = new ApiError(
            Instant.now(),
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "Requisicao invalida.",
            request.getRequestURI(),
            fields);

        return ResponseEntity.badRequest().body(body);
    }

    /** Rota inexistente. Sem isto o Spring devolveria a pagina de erro padrao, fora do contrato. */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> onUnknownRoute(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(
            HttpStatus.NOT_FOUND.value(),
            HttpStatus.NOT_FOUND.getReasonPhrase(),
            "Recurso nao encontrado.",
            request.getRequestURI()));
    }

    private static String messageOf(FieldError error) {
        return error.getDefaultMessage() != null ? error.getDefaultMessage() : "valor invalido";
    }
}
