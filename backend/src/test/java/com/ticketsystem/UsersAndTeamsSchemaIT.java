package com.ticketsystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketsystem.support.IntegrationTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * As regras de users, teams e team_memberships que so existem quando o Postgres executa.
 *
 * <p>Nao ha equivalente em Java ainda — as entidades chegam nas Fases 2 e 3. Sao
 * constraints de schema, e por isso o teste vem antes da implementacao
 * (CLAUDE.md#testes): com repository mockado, um teste destes provaria apenas que o mock
 * foi chamado, nunca que a regra vale.
 *
 * <p>Sem {@code @Transactional} de proposito: no Postgres uma constraint violada aborta
 * a transacao inteira, entao um teste transacional nao conseguiria afirmar duas
 * violacoes seguidas. Cada comando roda em autocommit e {@link #limparDadosDoTeste()}
 * devolve o banco ao estado anterior.
 *
 * <p>O container do Testcontainers e reaproveitado entre rodadas, entao nenhuma
 * assercao aqui olha contagem global de tabela: residuo de uma rodada interrompida
 * derrubaria um teste que nao tem nada a ver com a causa. Tudo e consultado pelo
 * prefixo {@value #PREFIXO} ou pelo dado especifico em questao.
 */
@IntegrationTest
class UsersAndTeamsSchemaIT {

    private static final String PREFIXO = "schema-it-";

    /** Hash BCrypt descartavel: a maioria destes testes exercita o schema, nao a senha. */
    private static final String HASH_QUALQUER =
            "$2a$10$TqZk9EsL91XXwsirPJRFpugEf.AsiMXF2h.Pr1z1FQ4GjfCwiHpBC";

    /** A senha que o README publica e que application-test.yml semeia ja em hash. */
    private static final String SENHA_DO_ADMIN_EM_TESTE = "admin123";

    @Autowired
    private JdbcClient jdbc;

    @Value("${spring.flyway.placeholders.admin_email}")
    private String emailDoAdminSemeado;

    @AfterEach
    void limparDadosDoTeste() {
        jdbc.sql(
                        """
                        DELETE FROM team_memberships
                        WHERE user_id IN (SELECT id FROM users WHERE email LIKE :prefixo)
                           OR team_id IN (SELECT id FROM teams WHERE name LIKE :prefixo)
                        """)
                .param("prefixo", PREFIXO + "%")
                .update();
        jdbc.sql("DELETE FROM users WHERE email LIKE :prefixo")
                .param("prefixo", PREFIXO + "%")
                .update();
        jdbc.sql("DELETE FROM teams WHERE name LIKE :prefixo")
                .param("prefixo", PREFIXO + "%")
                .update();
    }

    @Test
    @DisplayName("o mesmo e-mail nao entra duas vezes")
    void emailDuplicadoEhRejeitado() {
        criarUsuario(PREFIXO + "ana@exemplo.com", "AGENT");

        assertThatThrownBy(() -> criarUsuario(PREFIXO + "ana@exemplo.com", "AGENT"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("e-mail com maiuscula nao entra: a coluna so guarda minusculas")
    void emailForaDeMinusculasEhRejeitado() {
        // E o que faz a unicidade valer sem distinguir caixa usando um UNIQUE comum, e o
        // que impede 'Ana@x.com' e 'ana@x.com' virarem duas contas — duas visibilidades
        // diferentes sobre os mesmos tickets. Normalizar aqui e mais barato do que contar
        // com todo INSERT futuro lembrar de chamar lower().
        assertThatThrownBy(() -> criarUsuario(PREFIXO + "ANA@EXEMPLO.COM", "AGENT"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("papel global fora de REQUESTER/AGENT/ADMIN nao entra")
    void papelGlobalForaDoEnumEhRejeitado() {
        assertThatThrownBy(() -> criarUsuario(PREFIXO + "chefe@exemplo.com", "BOSS"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest(name = "aceita {0}")
    @ValueSource(
            strings = {
                // O Spring emite $2a; o htpasswd que o .env.example sugere emite $2y; $2b
                // aparece em hashes vindos de outras bibliotecas. Os tres sao BCrypt
                // valido. Sem este caso positivo, apertar a regex do CHECK deixaria a
                // suite verde e quebraria quem gerou o hash pelo caminho documentado.
                "$2a$10$TqZk9EsL91XXwsirPJRFpugEf.AsiMXF2h.Pr1z1FQ4GjfCwiHpBC",
                "$2b$12$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXY01",
                "$2y$04$./abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXY"
            })
    @DisplayName("hash BCrypt legitimo entra")
    void hashBcryptLegitimoEhAceito(String hash) {
        assertThat(criarUsuarioComHash(PREFIXO + "valido@exemplo.com", hash)).isPositive();
    }

    @ParameterizedTest(name = "recusa {0}")
    @ValueSource(
            strings = {
                "senha-em-texto-puro",
                // A falha que originou o CHECK: com ADMIN_PASSWORD_HASH nao definida, o
                // Spring deixa o placeholder como texto literal e a migration de seed
                // gravou esta string como se fosse a senha do admin. A aplicacao subiu
                // normalmente e so o primeiro login revelaria.
                "${ADMIN_PASSWORD_HASH}",
                // O irmao silencioso do caso acima: prefixo certo, corpo cortado. Passaria
                // por qualquer checagem que olhasse so o inicio, e o admin ficaria sem
                // conseguir entrar para sempre, de novo sem nada avisar.
                "$2a$10$TqZk9EsL91XXwsirPJRFpu",
                "$2a$10$",
                "$2a$10$senha-em-texto-puro-com-um-prefixo-de-bcrypt-grudado",
                // O custo precisa ser numerico de dois digitos.
                "$2a$xx$TqZk9EsL91XXwsirPJRFpugEf.AsiMXF2h.Pr1z1FQ4GjfCwiHpBC",
                // Outro algoritmo exige afrouxar o CHECK numa migration, deliberadamente.
                "$argon2id$v=19$m=65536,t=3,p=4$c29tZXNhbHQ$abcdefghijklmnop"
            })
    @DisplayName("o que nao for um BCrypt completo nao entra na coluna de senha")
    void senhaForaDoFormatoDeHashEhRejeitada(String valor) {
        assertThatThrownBy(() -> criarUsuarioComHash(PREFIXO + "invalido@exemplo.com", valor))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("um usuario participa de varias equipes: o UNIQUE e do par, nao do usuario")
    void usuarioPodePertencerAVariasEquipes() {
        long usuario = criarUsuario(PREFIXO + "multi@exemplo.com", "AGENT");
        long suporte = criarEquipe(PREFIXO + "suporte");
        long infra = criarEquipe(PREFIXO + "infra");

        criarVinculo(usuario, suporte, "MEMBER");
        criarVinculo(usuario, infra, "LEAD");

        assertThat(contarVinculosDoUsuario(usuario)).isEqualTo(2);
    }

    @Test
    @DisplayName("o mesmo usuario nao entra duas vezes na mesma equipe")
    void vinculoDuplicadoNaMesmaEquipeEhRejeitado() {
        long usuario = criarUsuario(PREFIXO + "repetido@exemplo.com", "AGENT");
        long equipe = criarEquipe(PREFIXO + "repetida");
        criarVinculo(usuario, equipe, "MEMBER");

        assertThatThrownBy(() -> criarVinculo(usuario, equipe, "LEAD"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("papel do vinculo fora de MEMBER/LEAD nao entra")
    void papelDoVinculoForaDoEnumEhRejeitado() {
        long usuario = criarUsuario(PREFIXO + "estagiario@exemplo.com", "AGENT");
        long equipe = criarEquipe(PREFIXO + "estagio");

        assertThatThrownBy(() -> criarVinculo(usuario, equipe, "OBSERVER"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("vinculo apontando para equipe inexistente nao entra")
    void vinculoParaEquipeInexistenteEhRejeitado() {
        long usuario = criarUsuario(PREFIXO + "orfao@exemplo.com", "AGENT");

        assertThatThrownBy(() -> criarVinculo(usuario, -1L, "MEMBER"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("nome de equipe repetido em caixa diferente e a mesma equipe")
    void nomeDeEquipeDuplicadoEhRejeitadoIndependenteDeCaixa() {
        // Ao contrario do e-mail, o nome da equipe guarda a caixa que a pessoa escreveu:
        // e texto de exibicao. Quem garante a unicidade e o indice funcional.
        criarEquipe(PREFIXO + "Suporte");

        assertThatThrownBy(() -> criarEquipe(PREFIXO + "SUPORTE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("apagar o usuario apaga os vinculos dele, sem deixar linha orfa")
    void apagarUsuarioApagaOsVinculos() {
        long usuario = criarUsuario(PREFIXO + "desligado@exemplo.com", "AGENT");
        long equipe = criarEquipe(PREFIXO + "desligamento");
        criarVinculo(usuario, equipe, "MEMBER");

        jdbc.sql("DELETE FROM users WHERE id = :id").param("id", usuario).update();

        assertThat(contarVinculosDoUsuario(usuario)).isZero();
    }

    @Test
    @DisplayName("apagar a equipe apaga os vinculos, mas preserva os usuarios")
    void apagarEquipeApagaOsVinculosMasPreservaOsUsuarios() {
        long usuario = criarUsuario(PREFIXO + "sobrevivente@exemplo.com", "AGENT");
        long equipe = criarEquipe(PREFIXO + "extinta");
        criarVinculo(usuario, equipe, "MEMBER");

        jdbc.sql("DELETE FROM teams WHERE id = :id").param("id", equipe).update();

        assertThat(contarVinculosDoUsuario(usuario)).isZero();
        assertThat(contarUsuariosComId(usuario)).isOne();
    }

    @Test
    @DisplayName("o admin semeado autentica com a senha que o README publica")
    void adminEhSemeadoComSenhaUtilizavel() {
        // lower(): a V3 normaliza o e-mail ao gravar, entao a busca normaliza tambem —
        // senao um ADMIN_EMAIL com maiuscula quebraria este teste sem nada estar errado.
        String hash = jdbc.sql(
                        """
                        SELECT password_hash FROM users
                        WHERE email = lower(:email) AND role = 'ADMIN'
                        """)
                .param("email", emailDoAdminSemeado)
                .query(String.class)
                .single();

        // Ter formato de hash nao basta: precisa ser o hash da senha que o README manda
        // usar. Sem esta assercao, um hash trocado no .env.example so apareceria la na
        // Fase 2, como um login que nao funciona e ninguem sabe por que.
        assertThat(new BCryptPasswordEncoder().matches(SENHA_DO_ADMIN_EM_TESTE, hash))
                .as("o hash semeado corresponde a senha documentada")
                .isTrue();
    }

    @Test
    @DisplayName("o elenco de demonstracao nao entra no perfil de teste")
    void seedDeDemonstracaoNaoEntraEmTeste() {
        // Os testes de visibilidade afirmam o que alguem NAO ve. Uma linha semeada que o
        // teste nao criou pode fazer um desses passar por acidente, entao o seed de
        // demonstracao fica restrito ao perfil dev. Se alguem ligar a location db/seed em
        // teste, este teste avisa.
        //
        // Procura o elenco pelo nome, em vez de contar linhas da tabela: um total global
        // falharia por residuo de qualquer outro teste, numa classe que ninguem tocou,
        // apontando para a causa errada.
        Long equipesDeDemonstracao = jdbc.sql(
                        """
                        SELECT count(*) FROM teams
                        WHERE lower(name) IN ('suporte n1', 'infraestrutura')
                        """)
                .query(Long.class)
                .single();

        assertThat(equipesDeDemonstracao).isZero();
    }

    private long criarUsuario(String email, String papel) {
        return criarUsuario(email, papel, HASH_QUALQUER);
    }

    private long criarUsuarioComHash(String email, String hash) {
        return criarUsuario(email, "AGENT", hash);
    }

    private long criarUsuario(String email, String papel, String hash) {
        return jdbc.sql(
                        """
                        INSERT INTO users (email, full_name, password_hash, role, created_at, updated_at)
                        VALUES (:email, :nome, :hash, :papel, :agora, :agora)
                        RETURNING id
                        """)
                .param("email", email)
                .param("nome", "Usuario de teste")
                .param("hash", hash)
                .param("papel", papel)
                .param("agora", agoraEmUtc())
                .query(Long.class)
                .single();
    }

    private long criarEquipe(String nome) {
        return jdbc.sql(
                        """
                        INSERT INTO teams (name, description, created_at, updated_at)
                        VALUES (:nome, :descricao, :agora, :agora)
                        RETURNING id
                        """)
                .param("nome", nome)
                .param("descricao", "Equipe de teste")
                .param("agora", agoraEmUtc())
                .query(Long.class)
                .single();
    }

    private void criarVinculo(long usuario, long equipe, String papel) {
        jdbc.sql(
                        """
                        INSERT INTO team_memberships (user_id, team_id, role, created_at, updated_at)
                        VALUES (:usuario, :equipe, :papel, :agora, :agora)
                        """)
                .param("usuario", usuario)
                .param("equipe", equipe)
                .param("papel", papel)
                .param("agora", agoraEmUtc())
                .update();
    }

    /**
     * O driver do Postgres nao infere o tipo SQL de um {@link java.time.Instant} em JDBC
     * cru — quem faz isso pelas entidades e o Hibernate. Aqui o tipo vai explicito, e
     * {@code timestamptz} normaliza para UTC de qualquer jeito.
     */
    private OffsetDateTime agoraEmUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private long contarVinculosDoUsuario(long usuario) {
        return jdbc.sql("SELECT count(*) FROM team_memberships WHERE user_id = :id")
                .param("id", usuario)
                .query(Long.class)
                .single();
    }

    private long contarUsuariosComId(long usuario) {
        return jdbc.sql("SELECT count(*) FROM users WHERE id = :id")
                .param("id", usuario)
                .query(Long.class)
                .single();
    }
}
