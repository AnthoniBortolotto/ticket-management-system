package com.ticketsystem.auth.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketsystem.auth.domain.RefreshToken;
import com.ticketsystem.auth.domain.RefreshTokenRepository;
import com.ticketsystem.auth.domain.RevocationReason;
import com.ticketsystem.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * O repositorio de refresh tokens contra um Postgres real.
 *
 * <p>Tambem e a primeira prova de que a entidade {@link RefreshToken} casa com a V4 — em
 * especial o {@code UUID}, que e o tipo mais provavel de divergir no mapeamento e
 * derrubaria todos os testes de integracao no boot.
 */
@IntegrationTest
class RefreshTokenRepositoryIT {

    private static final String PREFIXO = "refresh-it-";

    @Autowired
    private RefreshTokenRepository repositorio;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TransactionTemplate transacao;

    private long usuario;

    @BeforeEach
    void criarUsuario() {
        usuario = jdbc.sql(
                        """
                        INSERT INTO users (email, full_name, password_hash, role, created_at, updated_at)
                        VALUES (:email, 'Pessoa de Teste', :hash, 'AGENT', :agora, :agora)
                        RETURNING id
                        """)
                .param("email", PREFIXO + UUID.randomUUID() + "@exemplo.com")
                .param("hash", "$2a$10$TqZk9EsL91XXwsirPJRFpugEf.AsiMXF2h.Pr1z1FQ4GjfCwiHpBC")
                .param("agora", OffsetDateTime.now(ZoneOffset.UTC))
                .query(Long.class)
                .single();
    }

    @AfterEach
    void limpar() {
        jdbc.sql(
                        """
                        DELETE FROM refresh_tokens
                        WHERE user_id IN (SELECT id FROM users WHERE email LIKE :prefixo)
                        """)
                .param("prefixo", PREFIXO + "%")
                .update();
        jdbc.sql("DELETE FROM users WHERE email LIKE :prefixo")
                .param("prefixo", PREFIXO + "%")
                .update();
    }

    @Test
    @DisplayName("o token gravado volta pelo hash, com a familia intacta")
    void tokenGravadoVoltaPeloHash() {
        UUID familia = UUID.randomUUID();
        String hash = hashNumero(1);
        repositorio.save(RefreshToken.issue(usuario, hash, familia, daquiASeteDias()));

        var lido = transacao.execute(status -> repositorio.findForRotation(hash).orElseThrow());

        assertThat(lido.getFamilyId()).isEqualTo(familia);
        assertThat(lido.getUserId()).isEqualTo(usuario);
    }

    @Test
    @DisplayName("buscar para renovar fora de transacao e erro, e nao uma trava que nao trava")
    void buscarParaRenovarExigeTransacao() {
        repositorio.save(RefreshToken.issue(usuario, hashNumero(2), UUID.randomUUID(), daquiASeteDias()));

        // Uma trava pessimista que termina junto com a chamada nao protege nada. Melhor
        // falhar alto no primeiro uso errado do que parecer funcionar.
        assertThatThrownBy(() -> repositorio.findForRotation(hashNumero(2)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("revogar a familia derruba todos os tokens vivos dela")
    void revogarFamiliaDerrubaOsVivos() {
        UUID familia = UUID.randomUUID();
        repositorio.save(RefreshToken.issue(usuario, hashNumero(3), familia, daquiASeteDias()));
        repositorio.save(RefreshToken.issue(usuario, hashNumero(4), familia, daquiASeteDias()));

        int revogados = transacao.execute(
                status -> repositorio.revokeFamily(familia, Instant.now(), RevocationReason.REUSE_DETECTED));

        assertThat(revogados).isEqualTo(2);
        assertThat(motivo(hashNumero(3))).isEqualTo("REUSE_DETECTED");
        assertThat(motivo(hashNumero(4))).isEqualTo("REUSE_DETECTED");
    }

    @Test
    @DisplayName("revogar a familia nao reescreve o motivo de quem ja estava revogado")
    void revogarFamiliaPreservaMotivoOriginal() {
        UUID familia = UUID.randomUUID();
        repositorio.save(RefreshToken.issue(usuario, hashNumero(5), familia, daquiASeteDias()));
        transacao.executeWithoutResult(
                status -> repositorio.revokeFamily(familia, Instant.now(), RevocationReason.LOGOUT));
        repositorio.save(RefreshToken.issue(usuario, hashNumero(6), familia, daquiASeteDias()));

        transacao.executeWithoutResult(
                status -> repositorio.revokeFamily(familia, Instant.now(), RevocationReason.REUSE_DETECTED));

        // O primeiro motivo conta a historia. Sobrescreve-lo apagaria por que a sessao
        // terminou de verdade.
        assertThat(motivo(hashNumero(5))).isEqualTo("LOGOUT");
        assertThat(motivo(hashNumero(6))).isEqualTo("REUSE_DETECTED");
    }

    @Test
    @DisplayName("revogar uma familia nao toca nas outras sessoes do usuario")
    void revogarFamiliaNaoTocaOutrasFamilias() {
        UUID atacada = UUID.randomUUID();
        UUID outroDispositivo = UUID.randomUUID();
        repositorio.save(RefreshToken.issue(usuario, hashNumero(7), atacada, daquiASeteDias()));
        repositorio.save(RefreshToken.issue(usuario, hashNumero(8), outroDispositivo, daquiASeteDias()));

        transacao.executeWithoutResult(
                status -> repositorio.revokeFamily(atacada, Instant.now(), RevocationReason.REUSE_DETECTED));

        // E a razao de existir familia: um roubo detectado derruba a linhagem comprometida,
        // e nao todos os dispositivos da pessoa.
        assertThat(motivo(hashNumero(8))).isNull();
    }

    @Test
    @DisplayName("revogar atualiza o updated_at, mesmo sendo update em massa")
    void revogarAtualizaUpdatedAt() {
        UUID familia = UUID.randomUUID();
        repositorio.save(RefreshToken.issue(usuario, hashNumero(9), familia, daquiASeteDias()));
        OffsetDateTime antes = atualizadoEm(hashNumero(9));
        Instant depois = Instant.now().plus(Duration.ofMinutes(5));

        transacao.executeWithoutResult(
                status -> repositorio.revokeFamily(familia, depois, RevocationReason.LOGOUT));

        // Update em massa nao passa pela auditoria do Spring Data. Sem atualizar a coluna
        // na propria query, updated_at mentiria sobre a ultima mudanca de estado do token.
        assertThat(atualizadoEm(hashNumero(9)).toInstant()).isAfter(antes.toInstant());
    }

    private String motivo(String hash) {
        return jdbc.sql("SELECT revoked_reason FROM refresh_tokens WHERE token_hash = :hash")
                .param("hash", hash)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private OffsetDateTime atualizadoEm(String hash) {
        return jdbc.sql("SELECT updated_at FROM refresh_tokens WHERE token_hash = :hash")
                .param("hash", hash)
                .query(OffsetDateTime.class)
                .single();
    }

    private static Instant daquiASeteDias() {
        return Instant.now().plus(Duration.ofDays(7));
    }

    /** Um SHA-256 hexadecimal valido e distinto por numero, para o CHECK da V4 aceitar. */
    private static String hashNumero(int n) {
        return "%064x".formatted(n);
    }
}
