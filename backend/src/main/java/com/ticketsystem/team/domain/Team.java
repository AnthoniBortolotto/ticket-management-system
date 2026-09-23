package com.ticketsystem.team.domain;

import com.ticketsystem.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Um grupo de agentes que atende tickets junto.
 *
 * <p>Os membros nao moram aqui como colecao: cada vinculo e um {@link TeamMembership}
 * proprio. As perguntas que importam — "esta pessoa e membro?", "lidera?" — sao sobre um
 * par (equipe, usuario), e responde-las carregando a lista inteira seria pagar a equipe
 * toda para ler uma linha.
 *
 * <p>As larguras repetem as da V2, que sao contrato: contra Postgres, o
 * {@code ddl-auto: validate} nao confere tamanho de coluna.
 */
@Entity
@Table(name = "teams")
public class Team extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    /** Exigido pelo JPA. Nao use no codigo da aplicacao. */
    protected Team() {
    }

    public Team(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("nome da equipe e obrigatorio");
        }
        this.name = name.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "Team[id=%s, name=%s]".formatted(getId(), name);
    }
}
