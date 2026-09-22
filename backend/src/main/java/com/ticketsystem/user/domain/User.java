package com.ticketsystem.user.domain;

import com.ticketsystem.common.domain.BaseEntity;
import com.ticketsystem.user.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Locale;

/**
 * Uma pessoa que usa o sistema: identidade, credencial e papel global.
 *
 * <p>As larguras de coluna repetem as da migration V2 porque sao contrato, e o cabecalho
 * dela avisa: contra Postgres, o {@code ddl-auto: validate} NAO pega divergencia de
 * tamanho — ele compara o tipo, e o metadado do driver traz "varchar" sem o limite. Quem
 * cobraria seria o {@code INSERT}, em producao.
 *
 * <p>O e-mail e normalizado aqui, e nao por convencao de quem chama: a coluna tem
 * {@code CHECK (email = lower(email))}, entao um valor com maiuscula seria recusado pelo
 * banco no {@code INSERT}. Normalizar na entidade e o que garante que todo caminho de
 * escrita passa pela mesma regra.
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    /** Exigido pelo JPA. Nao use no codigo da aplicacao. */
    protected User() {
    }

    public User(String email, String fullName, String passwordHash, UserRole role) {
        this.email = normalizarEmail(email);
        this.fullName = exigirTexto(fullName, "nome");
        this.passwordHash = exigirTexto(passwordHash, "hash da senha");
        if (role == null) {
            throw new IllegalArgumentException("papel do usuario e obrigatorio");
        }
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    /**
     * O hash BCrypt da senha.
     *
     * <p>Nao deixe este valor sair do modulo {@code user}: quem precisa conferir senha
     * chama {@code UserFacade.authenticate}, que compara aqui dentro e devolve so o
     * resultado.
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = exigirTexto(passwordHash, "hash da senha");
    }

    /**
     * {@code Locale.ROOT} e nao o locale padrao: em turco, {@code lower('I')} devolve um
     * "i" sem ponto, e {@code ADMIN@X} viraria {@code admın@x}. O login simplesmente nao
     * acharia a conta, e so na maquina de quem tem esse idioma configurado.
     */
    private static String normalizarEmail(String email) {
        return exigirTexto(email, "e-mail").toLowerCase(Locale.ROOT);
    }

    private static String exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " e obrigatorio");
        }
        return valor.trim();
    }

    /** Sem o hash da senha: {@code toString} de entidade acaba em log e em excecao. */
    @Override
    public String toString() {
        return "User[id=%s, email=%s, role=%s]".formatted(getId(), email, role);
    }
}
