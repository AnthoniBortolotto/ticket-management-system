package com.ticketsystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketsystem.support.IntegrationTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * As regras de refresh_tokens e login_lockouts que so existem quando o Postgres executa.
 *
 * <p>Escrito antes da migration V4, pela regra do CLAUDE.md: constraint de schema e o caso
 * em que o teste de integracao <em>e</em> o nivel unitario — com repository mockado, ele
 * provaria que o mock foi chamado, nunca que o banco recusa.
 *
 * <p>Sem {@code @Transactional}: no Postgres uma constraint violada aborta a transacao
 * inteira, entao um teste transacional nao conseguiria afirmar duas violacoes seguidas.
 * A limpeza e por prefixo, e as tabelas novas sao limpas <strong>explicitamente</strong>,
 * sem confiar na cascata — senao o teste de cascata nao teria como falhar de verdade.
 */
@IntegrationTest
class RefreshTokensAndLockoutsSchemaIT {

    private static final String PREFIXO = "auth-schema-it-";

    /** Hash BCrypt descartavel: aqui o assunto e login, nao o formato da senha. */
    private static final String HASH_DE_SENHA =
            "$2a$10$TqZk9EsL91XXwsirPJRFpugEf.AsiMXF2h.Pr1z1FQ4GjfCwiHpBC";

    /** SHA-256 hexadecimal legitimo: 64 caracteres, minusculos. */
    private static final String HASH_DE_TOKEN =
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    @Autowired
    private JdbcClient jdbc;

    @AfterEach
    void limparDadosDoTeste() {
        jdbc.sql(
                        """
                        DELETE FROM refresh_tokens
                        WHERE user_id IN (SELECT id FROM users WHERE email LIKE :prefixo)
                        """)
                .param("prefixo", PREFIXO + "%")
                .update();
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

    // ---------------------------------------------------------------- refresh_tokens

    @Test
    @DisplayName("um refresh token valido entra")
    void refreshTokenValidoEhAceito() {
        long usuario = criarUsuario();

        assertThatCode(() -> criarRefreshToken(usuario, HASH_DE_TOKEN)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "recusa {0}")
    @ValueSource(
            strings = {
                // Um refresh token cru como o cliente o recebe: base64url, com maiusculas
                // e caracteres fora do alfabeto hexadecimal. E o erro que o CHECK existe
                // para pegar — gravar o token em vez do hash dele funciona perfeitamente,
                // o E2E passa, e o banco vira um cofre de credenciais em texto.
                "dGhpcy1pcy1hLXJhdy1yZWZyZXNoLXRva2VuLW5vdC1hLWhhc2g",
                // Hexadecimal, mas em maiusculas: dois formatos para o mesmo valor tornam
                // a busca por igualdade e a deteccao de reuso ambiguas.
                "9F86D081884C7D659A2FEAA0C55AD015A3BF4F1B2B0B822CD15D6C15B0F00A08",
                // Cortado e sobrando: 63 e 65 caracteres.
                "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a0",
                "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a080",
                "",
                // A armadilha ja documentada no CLAUDE.md: variavel de ambiente ausente
                // chega ao banco como o texto literal do placeholder.
                "${JWT_SECRET}"
            })
    @DisplayName("o que nao for um SHA-256 hexadecimal nao entra na coluna de hash")
    void hashDeTokenForaDoFormatoEhRejeitado(String valor) {
        long usuario = criarUsuario();

        assertThatThrownBy(() -> criarRefreshToken(usuario, valor))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("dois tokens com o mesmo hash nao coexistem")
    void hashDeTokenDuplicadoEhRejeitado() {
        long usuario = criarUsuario();
        criarRefreshToken(usuario, HASH_DE_TOKEN);

        // Sem esta unicidade, a busca por hash devolveria mais de uma linha e a deteccao
        // de reuso nao saberia qual delas ja foi usada.
        assertThatThrownBy(() -> criarRefreshToken(usuario, HASH_DE_TOKEN))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("token revogado sem motivo nao entra")
    void revogacaoSemMotivoEhRejeitada() {
        long usuario = criarUsuario();

        assertThatThrownBy(() -> criarRefreshTokenRevogado(usuario, HASH_DE_TOKEN, agora(), null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("motivo de revogacao sem instante de revogacao nao entra")
    void motivoSemRevogacaoEhRejeitado() {
        long usuario = criarUsuario();

        assertThatThrownBy(() -> criarRefreshTokenRevogado(usuario, HASH_DE_TOKEN, null, "LOGOUT"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("motivo de revogacao fora da lista nao entra")
    void motivoDeRevogacaoDesconhecidoEhRejeitado() {
        long usuario = criarUsuario();

        assertThatThrownBy(
                        () -> criarRefreshTokenRevogado(usuario, HASH_DE_TOKEN, agora(), "PORQUE_SIM"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("REUSE_DETECTED e um motivo valido de revogacao")
    void reuseDetectedEhMotivoValido() {
        long usuario = criarUsuario();

        // E o motivo que distingue "a sessao acabou" de "alguem fez replay de um token ja
        // rotacionado". Sem ele, o teste de reuso nao teria o que afirmar.
        assertThatCode(
                        () ->
                                criarRefreshTokenRevogado(
                                        usuario, HASH_DE_TOKEN, agora(), "REUSE_DETECTED"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("token que expira antes de ser criado nao entra")
    void validadeAnteriorACriacaoEhRejeitada() {
        long usuario = criarUsuario();

        assertThatThrownBy(
                        () ->
                                jdbc.sql(
                                                """
                                                INSERT INTO refresh_tokens (user_id, token_hash, family_id,
                                                                            expires_at, created_at, updated_at)
                                                VALUES (:usuario, :hash, :familia, :expira, :agora, :agora)
                                                """)
                                        .param("usuario", usuario)
                                        .param("hash", HASH_DE_TOKEN)
                                        .param("familia", UUID.randomUUID())
                                        .param("expira", agora().minusDays(1))
                                        .param("agora", agora())
                                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("token apontando para usuario inexistente nao entra")
    void refreshTokenOrfaoEhRejeitado() {
        assertThatThrownBy(() -> criarRefreshToken(-1L, HASH_DE_TOKEN))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --------------------------------------------------------------- login_lockouts

    @Test
    @DisplayName("um usuario tem no maximo um registro de bloqueio")
    void segundoBloqueioParaOMesmoUsuarioEhRejeitado() {
        long usuario = criarUsuario();
        criarBloqueio(usuario, 1);

        // "No maximo um por conta" e fato do banco, nao convencao do service: a chave
        // primaria e o proprio user_id.
        assertThatThrownBy(() -> criarBloqueio(usuario, 2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("contador de falhas negativo nao entra")
    void contadorNegativoEhRejeitado() {
        long usuario = criarUsuario();

        assertThatThrownBy(() -> criarBloqueio(usuario, -1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("bloqueio sem nenhuma falha registrada nao entra")
    void bloqueioSemFalhaEhRejeitado() {
        long usuario = criarUsuario();

        // Uma conta bloqueada sem nenhuma falha anterior seria estado impossivel: ou o
        // service tem um caminho que ninguem previu, ou alguem editou o banco na mao.
        assertThatThrownBy(
                        () ->
                                jdbc.sql(
                                                """
                                                INSERT INTO login_lockouts (user_id, failed_attempts, locked_until)
                                                VALUES (:usuario, 0, :ate)
                                                """)
                                        .param("usuario", usuario)
                                        .param("ate", agora().plusMinutes(15))
                                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("bloqueio apontando para usuario inexistente nao entra")
    void bloqueioOrfaoEhRejeitado() {
        assertThatThrownBy(() -> criarBloqueio(-1L, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("contar falha de login nao mexe no cadastro do usuario")
    void registrarFalhaNaoAlteraOUsuario() {
        long usuario = criarUsuario();
        OffsetDateTime antes = atualizadoEm(usuario);

        criarBloqueio(usuario, 1);
        jdbc.sql("UPDATE login_lockouts SET failed_attempts = 2 WHERE user_id = :id")
                .param("id", usuario)
                .update();

        // E a razao de o contador morar em tabela propria: em `users`, o `updated_at` da
        // auditoria passaria a significar "quando alguem errou a senha desta conta", e
        // trafego anonimo escreveria na linha do cadastro.
        assertThat(atualizadoEm(usuario))
                .as("users.updated_at nao pode mudar por causa de tentativa de login")
                .isEqualTo(antes);
    }

    // ------------------------------------------------------------------- cascata

    @Test
    @DisplayName("apagar o usuario apaga tokens e bloqueio, sem deixar credencial orfa")
    void apagarUsuarioApagaTokensEBloqueio() {
        long usuario = criarUsuario();
        criarRefreshToken(usuario, HASH_DE_TOKEN);
        criarBloqueio(usuario, 3);

        jdbc.sql("DELETE FROM users WHERE id = :id").param("id", usuario).update();

        // Um refresh token orfao e uma credencial viva apontando para conta que nao
        // existe mais. E a unica cascata que existe: a entidade guarda `Long userId` puro,
        // sem @ManyToOne, porque `auth` nao pode referenciar classe interna de `user`.
        assertThat(contar("refresh_tokens", usuario)).isZero();
        assertThat(contar("login_lockouts", usuario)).isZero();
    }

    // ------------------------------------------------------------------- helpers

    private long criarUsuario() {
        return jdbc.sql(
                        """
                        INSERT INTO users (email, full_name, password_hash, role, created_at, updated_at)
                        VALUES (:email, :nome, :hash, 'AGENT', :agora, :agora)
                        RETURNING id
                        """)
                .param("email", PREFIXO + UUID.randomUUID() + "@exemplo.com")
                .param("nome", "Usuario de teste")
                .param("hash", HASH_DE_SENHA)
                .param("agora", agora())
                .query(Long.class)
                .single();
    }

    private void criarRefreshToken(long usuario, String hash) {
        criarRefreshTokenRevogado(usuario, hash, null, null);
    }

    private void criarRefreshTokenRevogado(
            long usuario, String hash, OffsetDateTime revogadoEm, String motivo) {
        jdbc.sql(
                        """
                        INSERT INTO refresh_tokens (user_id, token_hash, family_id, expires_at,
                                                    revoked_at, revoked_reason, created_at, updated_at)
                        VALUES (:usuario, :hash, :familia, :expira, :revogado, :motivo, :agora, :agora)
                        """)
                .param("usuario", usuario)
                .param("hash", hash)
                .param("familia", UUID.randomUUID())
                .param("expira", agora().plusDays(7))
                .param("revogado", revogadoEm)
                .param("motivo", motivo)
                .param("agora", agora())
                .update();
    }

    private void criarBloqueio(long usuario, int tentativas) {
        jdbc.sql(
                        """
                        INSERT INTO login_lockouts (user_id, failed_attempts, last_failed_at)
                        VALUES (:usuario, :tentativas, :agora)
                        """)
                .param("usuario", usuario)
                .param("tentativas", tentativas)
                .param("agora", agora())
                .update();
    }

    private OffsetDateTime atualizadoEm(long usuario) {
        return jdbc.sql("SELECT updated_at FROM users WHERE id = :id")
                .param("id", usuario)
                .query(OffsetDateTime.class)
                .single();
    }

    private long contar(String tabela, long usuario) {
        // Nome de tabela nao vem de fora: sao os dois literais deste arquivo.
        return jdbc.sql("SELECT count(*) FROM " + tabela + " WHERE user_id = :id")
                .param("id", usuario)
                .query(Long.class)
                .single();
    }

    /**
     * O driver do Postgres nao infere o tipo SQL de um {@code Instant} em JDBC cru — quem
     * faz isso pelas entidades e o Hibernate. Aqui o tipo vai explicito.
     */
    private OffsetDateTime agora() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
