package com.ticketsystem.common.error;

import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
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

    /**
     * Qualquer excecao de dominio, de qualquer modulo.
     *
     * <p>Um metodo so para todas: o modulo declara o {@link ProblemKind} e este advice
     * traduz para status. E o que permite o advice ser unico sem que {@code common}
     * importe {@code auth}, {@code ticket} ou qualquer outro — o import inverso fecharia
     * um ciclo e o {@code ModularityTest} quebraria o build.
     */
    @ExceptionHandler(DomainException.class)
    ProblemDetail aoFalharRegraDeDominio(DomainException excecao) {
        ProblemDetail corpo =
                ProblemDetail.forStatusAndDetail(excecao.kind().status(), excecao.detail());
        corpo.setTitle(excecao.title());
        return corpo;
    }

    /**
     * Credencial ausente, invalida ou expirada.
     *
     * <p>Estas excecoes nascem dentro da cadeia de filtros do Spring Security, antes do
     * {@code DispatcherServlet}, e por isso NAO chegariam aqui sozinhas: o
     * {@code SecurityConfig} as devolve para o {@code HandlerExceptionResolver}
     * explicitamente. Sem isso a API teria dois formatos de erro — {@code ProblemDetail}
     * para o dominio e o padrao do Boot para autenticacao.
     *
     * <p>O {@code detail} e fixo: a mensagem original descreve o mecanismo interno
     * ("Bad credentials", "Jwt expired at ...") e as vezes o identificador tentado. Isso
     * ajuda quem ataca e nao ajuda quem integra.
     */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail aoFalharAutenticacao(AuthenticationException excecao) {
        logger.debug("Autenticacao recusada", excecao);

        ProblemDetail corpo = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Credenciais ausentes ou invalidas.");
        corpo.setTitle("Nao autenticado");
        return corpo;
    }

    /**
     * Autenticado, mas sem permissao para o que pediu.
     *
     * <p>A diferenca entre isto e 401 e a pergunta que cada um responde: 401 e "nao sei
     * quem voce e", 403 e "sei, e voce nao pode". Confundir os dois e o erro mais comum de
     * configuracao de seguranca, e por isso os testes cobrem o par.
     */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail aoNegarAcesso(AccessDeniedException excecao) {
        logger.debug("Acesso negado", excecao);

        ProblemDetail corpo = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "Sem permissao para executar esta operacao.");
        corpo.setTitle("Acesso negado");
        return corpo;
    }
}
