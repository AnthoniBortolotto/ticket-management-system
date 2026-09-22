package com.ticketsystem.common.error;

/**
 * Base das excecoes de dominio de todos os modulos.
 *
 * <p>Uma excecao que herda daqui ja sai como {@code ProblemDetail} no formato da RFC 9457,
 * com o status que o {@link ProblemKind} determina, sem que
 * {@link GlobalExceptionHandler} precise de um metodo por excecao — e, o que importa mais,
 * sem que {@code common} precise importar nada de modulo nenhum. A dependencia anda num
 * sentido so, e e isso que mantem o grafo de modulos aciclico.
 *
 * <p>Ao criar uma excecao nova: escolha o {@link ProblemKind} pelo <em>significado</em>, e
 * lembre que {@code title} e {@code detail} vao no corpo da resposta. Nunca ponha ali
 * nome de classe, mensagem de biblioteca ou qualquer coisa que descreva o mecanismo
 * interno: isso e superficie de ataque, nao diagnostico. O que ajuda a depurar vai para o
 * log.
 *
 * <p>Nao guarda causa por padrao de proposito — a causa quase sempre carrega detalhe de
 * implementacao. Quem precisar dela usa o construtor com {@code cause}, e ela fica so no
 * log.
 */
public abstract class DomainException extends RuntimeException {

    private final ProblemKind kind;
    private final String title;

    protected DomainException(ProblemKind kind, String title, String detail) {
        this(kind, title, detail, null);
    }

    protected DomainException(ProblemKind kind, String title, String detail, Throwable cause) {
        super(detail, cause);
        this.kind = kind;
        this.title = title;
    }

    public ProblemKind kind() {
        return kind;
    }

    /** Resumo curto e estavel do problema; vai no campo {@code title} da resposta. */
    public String title() {
        return title;
    }

    /**
     * Explicacao para quem consome a API; vai no campo {@code detail}.
     *
     * <p>E a mensagem da excecao, entao ela e escrita para ser lida de fora.
     */
    public String detail() {
        return getMessage();
    }
}
