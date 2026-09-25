package com.ticketsystem.common.web;

import java.util.List;
import java.util.function.Function;

/**
 * O formato de pagina da API — de toda listagem, nao so da de tickets.
 *
 * <p>Record proprio, e nao o {@code Page} do Spring Data serializado: aquele formato e da
 * biblioteca, muda entre versoes e expoe detalhes como {@code pageable} e {@code sort} que o
 * frontend nao deveria ler. Este e contrato: mudar um campo aqui e {@code v2} para todas as
 * listagens de uma vez.
 *
 * @param page          numero da pagina, a partir de zero
 * @param size          tamanho pedido
 * @param totalElements quantos itens existem no total, em todas as paginas
 * @param totalPages    quantas paginas ha com este tamanho
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public PageResponse {
        content = List.copyOf(content);
    }

    public static <E, T> PageResponse<T> of(List<E> itens, Function<E, T> conversao, int page, int size, long totalElements) {
        return new PageResponse<>(
                itens.stream().map(conversao).toList(), page, size, totalElements,
                (int) ((totalElements + size - 1) / size));
    }
}
