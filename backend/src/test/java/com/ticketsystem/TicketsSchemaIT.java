package com.ticketsystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketsystem.support.IntegrationTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * As regras de tickets, ticket_comments e ticket_routes que so existem quando o Postgres
 * executa — escrito antes da V5, como manda o CLAUDE.md.
 *
 * <p>A mais importante e a dos dois modos de atribuicao: um ticket esta sempre em
 * <strong>exatamente um</strong>. A entidade tambem garante isso, mas qualquer
 * {@code UPDATE} escrito a mao, script de correcao ou adaptador futuro passa por fora da
 * entidade, e so a constraint o pega.
 *
 * <p>Sem {@code @Transactional}, pelo mesmo motivo do {@code UsersAndTeamsSchemaIT}: uma
 * constraint violada aborta a transacao inteira no Postgres.
 */
@IntegrationTest
class TicketsSchemaIT {

    private static final String PREFIXO = "tickets-schema-it-";

    @Autowired
    private JdbcClient jdbc;

    private long solicitante;
    private long agente;
    private long equipe;
    private long outraEquipe;

    @BeforeEach
    void montarPessoasEEquipes() {
        solicitante = criarUsuario("solicitante", "REQUESTER");
        agente = criarUsuario("agente", "AGENT");
        equipe = criarEquipe("Suporte");
        outraEquipe = criarEquipe("Infra");
    }

    @AfterEach
    void limpar() {
        // Os comentarios vao em cascata com o ticket; o ticket precisa sair antes das
        // pessoas e equipes, que ele segura por RESTRICT.
        jdbc.sql("DELETE FROM tickets WHERE requester_id IN (SELECT id FROM users WHERE email LIKE :p)")
                .param("p", PREFIXO + "%").update();
        jdbc.sql("DELETE FROM ticket_routes WHERE team_id IN (SELECT id FROM teams WHERE name LIKE :p)")
                .param("p", PREFIXO + "%").update();
        jdbc.sql("DELETE FROM users WHERE email LIKE :p").param("p", PREFIXO + "%").update();
        jdbc.sql("DELETE FROM teams WHERE name LIKE :p").param("p", PREFIXO + "%").update();
    }

    // --- modos de atribuicao --------------------------------------------------------------

    @Test
    @DisplayName("ticket em modo equipe entra, com ou sem responsavel atual")
    void modoEquipeEntra() {
        assertThat(inserir(emModoEquipe())).isPositive();
        assertThat(inserir(emModoEquipe(Map.of("current_assignee_id", agente)))).isPositive();
    }

    @Test
    @DisplayName("ticket em modo exclusivo entra, com a equipe de origem preservada")
    void modoExclusivoEntra() {
        assertThat(inserir(emModoExclusivo())).isPositive();
    }

    @Test
    @DisplayName("os dois modos ao mesmo tempo nao entram")
    void doisModosAoMesmoTempoNaoEntram() {
        // O caso que a regra existe para impedir: equipe e responsavel exclusivo ao mesmo
        // tempo. Quem enxerga o ticket passaria a depender de qual coluna a consulta olha.
        var colunas = emModoExclusivo();
        colunas.put("assigned_team_id", equipe);

        assertThatThrownBy(() -> inserir(colunas)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("ticket sem modo nenhum nao entra")
    void semModoNaoEntra() {
        // Um ticket sem equipe e sem responsavel nao e visto por membro nenhum — so por admin
        // e pelo solicitante. Ninguem o atenderia, e nada avisaria.
        var colunas = emModoEquipe();
        colunas.put("assigned_team_id", null);

        assertThatThrownBy(() -> inserir(colunas)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("modo exclusivo sem equipe de origem nao entra")
    void exclusivoSemOrigemNaoEntra() {
        // Sem a origem, o lider da equipe perderia de vista um ticket que saiu dela, e ninguem
        // saberia para onde devolve-lo.
        var colunas = emModoExclusivo();
        colunas.put("origin_team_id", null);

        assertThatThrownBy(() -> inserir(colunas)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("equipe de origem em modo equipe nao entra")
    void origemEmModoEquipeNaoEntra() {
        // Origem so tem significado em modo exclusivo. Deixada para tras ao voltar para a
        // equipe, ela daria ao lider de outra equipe uma visibilidade que ninguem concedeu.
        assertThatThrownBy(() -> inserir(emModoEquipe(Map.of("origin_team_id", outraEquipe))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("responsavel atual em modo exclusivo nao entra")
    void responsavelAtualEmModoExclusivoNaoEntra() {
        // Em modo exclusivo quem atua e o responsavel exclusivo; um responsavel atual ali
        // seria uma segunda pessoa "atendendo" um ticket que so uma pode ver.
        var colunas = emModoExclusivo();
        colunas.put("current_assignee_id", agente);

        assertThatThrownBy(() -> inserir(colunas)).isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- valores de dominio ---------------------------------------------------------------

    @ParameterizedTest(name = "{0} = {1} nao entra")
    @CsvSource({"status, DONE", "priority, CRITICAL", "category, NETWORK"})
    @DisplayName("valor fora do enum nao entra")
    void valorForaDoEnumNaoEntra(String coluna, String valor) {
        assertThatThrownBy(() -> inserir(emModoEquipe(Map.of(coluna, valor))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest(name = "status {0} entra")
    @ValueSource(strings = {"OPEN", "IN_PROGRESS", "WAITING_CUSTOMER", "RESOLVED", "CLOSED", "REOPENED"})
    @DisplayName("todo status do fluxo entra")
    void todoStatusEntra(String status) {
        // O par do teste acima: sem ele, um CHECK que esquecesse um valor quebraria so em
        // producao, na primeira transicao para ele.
        assertThat(inserir(emModoEquipe(Map.of("status", status)))).isPositive();
    }

    @Test
    @DisplayName("titulo em branco nao entra")
    void tituloEmBrancoNaoEntra() {
        assertThatThrownBy(() -> inserir(emModoEquipe(Map.of("title", "   "))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- integridade referencial ----------------------------------------------------------

    @Test
    @DisplayName("quem abriu ticket nao pode ser apagado")
    void solicitanteComTicketNaoEhApagado() {
        // Ticket e historico. Cascata aqui apagaria chamados — e os comentarios deles —
        // junto com a conta de quem os abriu.
        inserir(emModoEquipe());

        assertThatThrownBy(() -> jdbc.sql("DELETE FROM users WHERE id = :id").param("id", solicitante).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("equipe com ticket nao pode ser apagada")
    void equipeComTicketNaoEhApagada() {
        inserir(emModoEquipe());

        assertThatThrownBy(() -> jdbc.sql("DELETE FROM teams WHERE id = :id").param("id", equipe).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("comentario vai junto com o ticket, e nunca entra em branco")
    void comentarioSegueOTicket() {
        long ticket = inserir(emModoEquipe());
        comentar(ticket, "Primeira resposta", false);

        assertThatThrownBy(() -> comentar(ticket, "  ", true)).isInstanceOf(DataIntegrityViolationException.class);

        jdbc.sql("DELETE FROM tickets WHERE id = :id").param("id", ticket).update();
        assertThat(jdbc.sql("SELECT count(*) FROM ticket_comments WHERE ticket_id = :id")
                        .param("id", ticket).query(Long.class).single())
                .isZero();
    }

    @Test
    @DisplayName("comentario sem a flag de interno nao entra")
    void comentarioSemFlagNaoEntra() {
        // A flag decide se o solicitante le o comentario. Um NULL ali deixaria a decisao para
        // a interpretacao de cada consulta.
        long ticket = inserir(emModoEquipe());

        assertThatThrownBy(() -> comentar(ticket, "Sem flag", null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- roteamento -----------------------------------------------------------------------

    @Test
    @DisplayName("cada categoria tem no maximo uma rota")
    void umaRotaPorCategoria() {
        rotear("HARDWARE", equipe);

        assertThatThrownBy(() -> rotear("HARDWARE", outraEquipe)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("rota para categoria fora do enum nao entra")
    void rotaForaDoEnumNaoEntra() {
        assertThatThrownBy(() -> rotear("NETWORK", equipe)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("equipe que recebe uma categoria nao pode ser apagada")
    void equipeRoteadaNaoEhApagada() {
        // Apagada em cascata, a rota sumiria em silencio e a proxima abertura daquela
        // categoria falharia sem ninguem ter mexido no roteamento.
        rotear("SOFTWARE", equipe);

        assertThatThrownBy(() -> jdbc.sql("DELETE FROM teams WHERE id = :id").param("id", equipe).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- apoio ----------------------------------------------------------------------------

    private Map<String, Object> emModoEquipe() {
        return emModoEquipe(Map.of());
    }

    private Map<String, Object> emModoEquipe(Map<String, Object> ajustes) {
        Map<String, Object> colunas = new HashMap<>();
        colunas.put("title", "Impressora nao imprime");
        colunas.put("description", "Desde ontem.");
        colunas.put("status", "OPEN");
        colunas.put("priority", "MEDIUM");
        colunas.put("category", "HARDWARE");
        colunas.put("requester_id", solicitante);
        colunas.put("assigned_team_id", equipe);
        colunas.put("current_assignee_id", null);
        colunas.put("exclusive_assignee_id", null);
        colunas.put("origin_team_id", null);
        colunas.putAll(ajustes);
        return colunas;
    }

    private Map<String, Object> emModoExclusivo() {
        var colunas = emModoEquipe();
        colunas.put("assigned_team_id", null);
        colunas.put("exclusive_assignee_id", agente);
        colunas.put("origin_team_id", equipe);
        return colunas;
    }

    private long inserir(Map<String, Object> colunas) {
        return jdbc.sql(
                        """
                        INSERT INTO tickets (title, description, status, priority, category, requester_id,
                                             assigned_team_id, current_assignee_id, exclusive_assignee_id,
                                             origin_team_id, version, created_at, updated_at)
                        VALUES (:title, :description, :status, :priority, :category, :requester_id,
                                :assigned_team_id, :current_assignee_id, :exclusive_assignee_id,
                                :origin_team_id, 0, :agora, :agora)
                        RETURNING id
                        """)
                .params(colunas)
                .param("agora", agora())
                .query(Long.class)
                .single();
    }

    private void comentar(long ticket, String corpo, Boolean interno) {
        jdbc.sql(
                        """
                        INSERT INTO ticket_comments (ticket_id, author_id, body, internal, created_at, updated_at)
                        VALUES (:ticket, :autor, :corpo, :interno, :agora, :agora)
                        """)
                .param("ticket", ticket)
                .param("autor", agente)
                .param("corpo", corpo)
                .param("interno", interno)
                .param("agora", agora())
                .update();
    }

    private void rotear(String categoria, long paraEquipe) {
        jdbc.sql(
                        """
                        INSERT INTO ticket_routes (category, team_id, created_at, updated_at)
                        VALUES (:categoria, :equipe, :agora, :agora)
                        """)
                .param("categoria", categoria)
                .param("equipe", paraEquipe)
                .param("agora", agora())
                .update();
    }

    private long criarUsuario(String apelido, String papel) {
        return jdbc.sql(
                        """
                        INSERT INTO users (email, full_name, password_hash, role, created_at, updated_at)
                        VALUES (:email, 'Pessoa de Teste', :hash, :papel, :agora, :agora)
                        RETURNING id
                        """)
                .param("email", PREFIXO + apelido + "-" + System.nanoTime() + "@exemplo.com")
                .param("hash", "$2a$10$TqZk9EsL91XXwsirPJRFpugEf.AsiMXF2h.Pr1z1FQ4GjfCwiHpBC")
                .param("papel", papel)
                .param("agora", agora())
                .query(Long.class)
                .single();
    }

    private long criarEquipe(String nome) {
        return jdbc.sql("INSERT INTO teams (name, created_at, updated_at) VALUES (:nome, :agora, :agora) RETURNING id")
                .param("nome", PREFIXO + nome + "-" + System.nanoTime())
                .param("agora", agora())
                .query(Long.class)
                .single();
    }

    private static OffsetDateTime agora() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
