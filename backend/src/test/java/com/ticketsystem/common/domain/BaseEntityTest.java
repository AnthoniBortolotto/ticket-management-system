package com.ticketsystem.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

/**
 * A igualdade de entidade parece detalhe e nao e: com {@code equals} errado, uma entidade
 * ainda nao persistida some de um {@code Set} depois do flush, ou duas linhas diferentes
 * passam a ser a mesma. Por isso a regra tem teste mesmo estando em codigo transversal.
 */
class BaseEntityTest {

    @Test
    void duasInstanciasSemIdNuncaSaoIguais() {
        var uma = comId(null);
        var outra = comId(null);

        assertThat(uma).isNotEqualTo(outra);
    }

    @Test
    void mesmaInstanciaSemIdEIgualASiPropria() {
        var entidade = comId(null);

        assertThat(entidade).isEqualTo(entidade);
    }

    @Test
    void instanciasComOMesmoIdSaoIguais() {
        assertThat(comId(42L)).isEqualTo(comId(42L));
    }

    @Test
    void instanciasComIdsDiferentesNaoSaoIguais() {
        assertThat(comId(42L)).isNotEqualTo(comId(43L));
    }

    @Test
    void tiposDiferentesComOMesmoIdNaoSaoIguais() {
        var entidade = comId(42L);
        var deOutroTipo = new OutraEntidade();
        atribuirId(deOutroTipo, 42L);

        assertThat(entidade).isNotEqualTo(deOutroTipo);
    }

    @Test
    void oHashCodeSobreviveAAtribuicaoDoId() {
        var entidade = comId(null);
        int antes = entidade.hashCode();

        atribuirId(entidade, 42L);

        // Se o hash mudasse ao persistir, a entidade sumiria de qualquer HashSet.
        assertThat(entidade.hashCode()).isEqualTo(antes);
    }

    private static EntidadeDeTeste comId(Long id) {
        var entidade = new EntidadeDeTeste();
        if (id != null) {
            atribuirId(entidade, id);
        }
        return entidade;
    }

    /** O id e atribuido pelo provedor JPA; no teste o reflection faz o papel dele. */
    private static void atribuirId(BaseEntity entidade, Long id) {
        try {
            Field campo = BaseEntity.class.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(entidade, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("BaseEntity.id mudou de nome", e);
        }
    }

    private static final class EntidadeDeTeste extends BaseEntity {
    }

    private static final class OutraEntidade extends BaseEntity {
    }
}
