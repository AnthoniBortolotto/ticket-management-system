package com.ticketsystem.auth.domain;

import com.ticketsystem.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Uma sessao renovavel, guardada pelo hash do valor.
 *
 * <p>O valor que o cliente carrega nunca e gravado — so o SHA-256 dele. Quem ler esta
 * tabela, de um dump, de um log ou de um backup, nao consegue usar o que leu.
 *
 * <p>Cada renovacao consome o token atual e emite outro na mesma {@code familyId}. Isso e
 * o que torna o roubo detectavel: um token ja consumido que aparece de novo so pode ter
 * vindo de uma copia, e a familia inteira e revogada.
 *
 * <p>{@code userId} e um {@code Long} puro, sem {@code @ManyToOne User}: {@code auth} nao
 * pode referenciar classe interna do modulo {@code user}, e o {@code ModularityTest}
 * quebraria o build. Sem associacao JPA, a cascata ao apagar o usuario e a do banco.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

    /** O mesmo formato do CHECK da V4: SHA-256 em hexadecimal minusculo. */
    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-f]{64}$");

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", length = 20)
    private RevocationReason revokedReason;

    /** Exigido pelo JPA. */
    protected RefreshToken() {
    }

    private RefreshToken(Long userId, String tokenHash, UUID familyId, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
    }

    /**
     * Um token novo.
     *
     * <p>Recusa hash fora do formato antes do banco recusar: gravar o valor cru no lugar do
     * hash funciona perfeitamente em todos os fluxos, e e justamente por isso que precisa
     * ser pego cedo.
     */
    public static RefreshToken issue(Long userId, String tokenHash, UUID familyId, Instant expiresAt) {
        if (tokenHash == null || !SHA256_HEX.matcher(tokenHash).matches()) {
            throw new IllegalArgumentException("o token precisa ser guardado como SHA-256 hexadecimal");
        }
        return new RefreshToken(userId, tokenHash, familyId, expiresAt);
    }

    /** Ja foi usado numa renovacao ou revogado. Em qualquer dos casos, nao vale mais. */
    public boolean wasConsumed() {
        return usedAt != null || revokedAt != null;
    }

    /** "Valido ate X" deixa de valer em X. */
    public boolean isExpiredAt(Instant instant) {
        return !instant.isBefore(expiresAt);
    }

    /**
     * Consome o token numa renovacao.
     *
     * <p>Chamar isto num token ja consumido e erro de programacao — o service devia ter
     * tratado o replay antes —, entao a excecao e de estado, e nao de dominio.
     */
    public void markUsed(Instant at) {
        if (wasConsumed()) {
            throw new IllegalStateException("refresh token ja consumido nao pode ser usado de novo");
        }
        this.usedAt = at;
    }

    /**
     * Encerra o token. Revogar de novo nao faz nada: o primeiro motivo e o que conta a
     * historia, e sobrescreve-lo apagaria por que a sessao terminou.
     */
    public void revoke(Instant at, RevocationReason reason) {
        if (revokedAt != null) {
            return;
        }
        this.revokedAt = at;
        this.revokedReason = reason;
    }

    public Long getUserId() {
        return userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public RevocationReason getRevokedReason() {
        return revokedReason;
    }

    /** Sem o hash: {@code toString} de entidade acaba em log e em mensagem de excecao. */
    @Override
    public String toString() {
        return "RefreshToken[id=%s, userId=%s, familyId=%s]".formatted(getId(), userId, familyId);
    }
}
