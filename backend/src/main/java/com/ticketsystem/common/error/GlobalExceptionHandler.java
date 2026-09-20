package com.ticketsystem.common.error;

import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Unico ponto que traduz excecao em resposta HTTP.
 *
 * <p>O corpo de erro segue a RFC 9457 ({@link ProblemDetail}), que o Spring ja produz
 * para as proprias excecoes. Herdar de {@link ResponseEntityExceptionHandler} e o que
 * liga esse comportamento: 404, 405, 415 e companhia saem no formato padrao sem uma
 * linha de codigo nossa.
 *
 * <p>Controller nao monta {@code ResponseEntity} com status na mao: ele lanca excecao de
 * dominio e o mapeamento para status vive aqui. Cada modulo novo registra aqui as suas
 * excecoes com {@code @ExceptionHandler}, e so aqui.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Bean Validation falhou em um DTO de entrada.
     *
     * <p>O {@code ProblemDetail} padrao diz apenas que a requisicao e invalida. Quem
     * consome precisa saber <em>qual campo</em> para destacar na tela, entao a lista vai
     * na propriedade {@code errors}, ordenada para a resposta ser estavel entre chamadas.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<FieldProblem> campos = exception.getBindingResult().getFieldErrors().stream()
            .map(FieldProblem::from)
            .sorted(Comparator.comparing(FieldProblem::field).thenComparing(FieldProblem::message))
            .toList();

        ProblemDetail corpo = exception.getBody();
        corpo.setTitle("Requisicao invalida");
        corpo.setDetail("Um ou mais campos foram rejeitados pela validacao.");
        corpo.setProperty("errors", campos);

        return handleExceptionInternal(exception, corpo, headers, status, request);
    }
}
