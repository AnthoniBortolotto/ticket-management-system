package com.ticketsystem.auth.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketsystem.auth.domain.LoginLockoutRepository;
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

/**
 * O contador de falhas contra um Postgres real.
 *
 * <p>A regra de recomeco da contagem — bloqueio vencido, falha antiga — mora num
 * {@code CASE} dentro do {@code ON CONFLICT DO UPDATE}. So existe quando o banco executa,
 * e com o repositorio mockado o teste provaria apenas que o mock foi chamado.
 *
 * <p>Escreve contra a interface de dominio, e nao contra o adaptador JDBC: e este mesmo
 * teste que um adaptador futuro precisaria passar sem ser reescrito.
 */
@IntegrationTest
class LoginLockoutRepositoryIT {

    private static final String PREFIXO = "lockout-it-";
    private static final Instant T0 = Instant.parse("2026-09-22T10:00:00Z");
    private static final Duration JANELA = Duration.ofMinutes(15);

    @Autowired
    private LoginLockoutRepository repositorio;

    @Autowired
    private JdbcClient jdbc;

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
        // Explicito, sem confiar na cascata: a cascata tem teste proprio no SchemaIT.
        jdbc.sql(
                        """
                        DELETE FROM login_lockouts
                        WHERE user_id IN (SELECT id FROM users WHERE email LIKE :prefixo)
                        """)
                .param("prefixo", PREFIXO + "%")
                .update();
        jdbc.sql("DELETE FROM users WHERE email LIKE :prefixo")
                .param("prefixo", PREFIXO + "%")
                .update();
    }

    @Test
    @DisplayName("a primeira falha cria o registro com contagem um")
    void primeiraFalhaCriaRegistro() {
        int total = repositorio.registerFailure(usuario, T0, T0.minus(JANELA));

        assertThat(total).isOne();
        assertThat(repositorio.findByUserId(usuario)).isPresent();
    }

    @Test
    @DisplayName("falhas seguidas e recentes se acumulam")
    void falhasRecentesAcumulam() {
        repositorio.registerFailure(usuario, T0, T0.minus(JANELA));
        repositorio.registerFailure(usuario, T0.plusSeconds(10), T0.plusSeconds(10).minus(JANELA));
        int total = repositorio.registerFailure(usuario, T0.plusSeconds(20), T0.plusSeconds(20).minus(JANELA));

        assertThat(total).isEqualTo(3);
    }

    @Test
    @DisplayName("falha depois que o bloqueio venceu recomeca a contagem e desbloqueia")
    void falhaAposBloqueioVencidoRecomeca() {
        repositorio.registerFailure(usuario, T0, T0.minus(JANELA));
        repositorio.lockUntil(usuario, T0.plus(JANELA));

        // Quem esperou o bloqueio passar volta a ter todas as tentativas, em vez de ser
        // rebloqueado na primeira senha errada.
        Instant depois = T0.plus(JANELA).plusSeconds(1);
        int total = repositorio.registerFailure(usuario, depois, depois.minus(JANELA));

        assertThat(total).isOne();
        assertThat(repositorio.findByUserId(usuario).orElseThrow().lockedUntil()).isNull();
    }

    @Test
    @DisplayName("falha muito antiga e esquecida, mesmo sem bloqueio")
    void falhaAntigaEhEsquecida() {
        repositorio.registerFailure(usuario, T0, T0.minus(JANELA));
        repositorio.registerFailure(usuario, T0.plusSeconds(5), T0.plusSeconds(5).minus(JANELA));

        // Sem isto, duas senhas erradas num dia e uma terceira semanas depois bloqueariam.
        Instant semanasDepois = T0.plus(Duration.ofDays(20));
        int total = repositorio.registerFailure(usuario, semanasDepois, semanasDepois.minus(JANELA));

        assertThat(total).isOne();
    }

    @Test
    @DisplayName("enquanto o bloqueio vale, a contagem nao recomeca")
    void bloqueioVigenteNaoRecomeca() {
        repositorio.registerFailure(usuario, T0, T0.minus(JANELA));
        repositorio.registerFailure(usuario, T0.plusSeconds(1), T0.plusSeconds(1).minus(JANELA));
        repositorio.lockUntil(usuario, T0.plus(JANELA));

        Instant durante = T0.plusSeconds(60);
        int total = repositorio.registerFailure(usuario, durante, durante.minus(JANELA));

        assertThat(total).isEqualTo(3);
        assertThat(repositorio.findByUserId(usuario).orElseThrow().isLockedAt(durante)).isTrue();
    }

    @Test
    @DisplayName("o bloqueio gravado e lido de volta pelo instante certo")
    void bloqueioGravadoEhLido() {
        repositorio.registerFailure(usuario, T0, T0.minus(JANELA));
        repositorio.lockUntil(usuario, T0.plus(JANELA));

        var registro = repositorio.findByUserId(usuario).orElseThrow();

        assertThat(registro.lockedUntil()).isEqualTo(T0.plus(JANELA));
        assertThat(registro.isLockedAt(T0.plusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("limpar apaga o historico da conta")
    void limparApagaHistorico() {
        repositorio.registerFailure(usuario, T0, T0.minus(JANELA));

        repositorio.clear(usuario);

        assertThat(repositorio.findByUserId(usuario)).isEmpty();
    }

    @Test
    @DisplayName("contar falha nao toca na linha do usuario")
    void contarFalhaNaoAlteraOCadastro() {
        OffsetDateTime antes = atualizadoEm();

        repositorio.registerFailure(usuario, T0, T0.minus(JANELA));
        repositorio.lockUntil(usuario, T0.plus(JANELA));

        // A razao de o contador morar em tabela propria: trafego anonimo nao escreve no
        // cadastro, e users.updated_at continua significando "quando o cadastro mudou".
        assertThat(atualizadoEm()).isEqualTo(antes);
    }

    private OffsetDateTime atualizadoEm() {
        return jdbc.sql("SELECT updated_at FROM users WHERE id = :id")
                .param("id", usuario)
                .query(OffsetDateTime.class)
                .single();
    }
}
