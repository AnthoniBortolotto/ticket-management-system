package com.ticketsystem.common.error;

import org.springframework.http.HttpStatus;

/**
 * Que tipo de problema aconteceu, sem dizer qual numero HTTP ele vira.
 *
 * <p>Existe para resolver uma tensao concreta entre duas regras do projeto. O CLAUDE.md
 * exige um {@code @RestControllerAdvice} unico e proibe o controller montar
 * {@code ResponseEntity} com status na mao — logo a traducao para HTTP mora no advice, em
 * {@code common}. Mas se o advice importasse as excecoes de {@code auth} ou de
 * {@code ticket}, {@code common} passaria a depender desses modulos, que ja dependem dele:
 * ciclo, e o {@code ModularityTest} quebra o build.
 *
 * <p>Com este enum a dependencia anda em um sentido so. O modulo declara o tipo de
 * problema; quem sabe traduzir tipo em status e {@code GlobalExceptionHandler}, e ele nao
 * precisa conhecer modulo nenhum.
 *
 * <p>Por isso tambem nao existe um valor por status HTTP: a lista abaixo e o vocabulario
 * do dominio. Se um caso novo nao couber em nenhum deles, o valor novo entra aqui e o
 * mapeamento fica visivel num lugar so.
 */
public enum ProblemKind {

    /** Entrada malformada ou que viola uma regra de negocio de conteudo. */
    INVALID(HttpStatus.BAD_REQUEST),

    /** Nao sabemos quem esta pedindo, ou a credencial apresentada nao vale. */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),

    /** Sabemos quem esta pedindo, e essa pessoa nao pode fazer isto. */
    FORBIDDEN(HttpStatus.FORBIDDEN),

    /** O recurso nao existe — ou nao existe <em>para quem pergunta</em>. */
    NOT_FOUND(HttpStatus.NOT_FOUND),

    /** O estado atual do sistema impede a operacao (duplicidade, transicao invalida). */
    CONFLICT(HttpStatus.CONFLICT),

    /**
     * O recurso existe mas esta temporariamente indisponivel por decisao do sistema.
     *
     * <p>423, e nao 429: 429 fala de <em>taxa</em> e some quando a rajada passa. Uma conta
     * bloqueada por tentativas de login continua bloqueada mesmo que ninguem mais tente.
     */
    LOCKED(HttpStatus.LOCKED);

    private final HttpStatus status;

    ProblemKind(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
