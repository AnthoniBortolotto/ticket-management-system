package com.ticketsystem.auth.web.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ticketsystem.support.IntegrationTest;
import com.ticketsystem.support.SecureMockMvc;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

/**
 * Login, bloqueio e sessao pelo endpoint, contra um Postgres real.
 *
 * <p>Os dois testes mais importantes daqui provam coisas que nenhum teste unitario
 * consegue, porque dependem de transacao de verdade:
 * <ul>
 *   <li>a falha de login contada sobrevive a excecao que o login lanca logo depois
 *       ({@code REQUIRES_NEW}) — senao o bloqueio nunca dispararia;</li>
 *   <li>a revogacao da familia num replay sobrevive a excecao que o refresh lanca logo
 *       depois ({@code noRollbackFor}) — senao o token do ladrao continuaria vivo.</li>
 * </ul>
 * Nos dois, a suite ficaria verde com o mecanismo quebrado se o teste olhasse so a
 * resposta. Por isso eles olham o banco.
 *
 * <p>Com {@code max-failed-attempts: 3} em {@code application-test.yml}.
 */
@IntegrationTest
class AuthControllerIT {

    private static final String PREFIXO = "auth-it-";
    private static final String SENHA = "senha-certa-do-teste";

    /** Um BCrypt so, pago uma vez: custa ~80ms e nao precisa ser por teste. */
    private static final String HASH_DA_SENHA = new BCryptPasswordEncoder().encode(SENHA);

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private JdbcClient jdbc;

    private MockMvc mvc;
    private String email;
    private long usuario;

    @BeforeEach
    void configurar() {
        mvc = SecureMockMvc.from(contexto);
        email = PREFIXO + UUID.randomUUID() + "@exemplo.com";
        usuario = jdbc.sql(
                        """
                        INSERT INTO users (email, full_name, password_hash, role, created_at, updated_at)
                        VALUES (:email, 'Pessoa de Teste', :hash, 'AGENT', :agora, :agora)
                        RETURNING id
                        """)
                .param("email", email)
                .param("hash", HASH_DA_SENHA)
                .param("agora", OffsetDateTime.now(ZoneOffset.UTC))
                .query(Long.class)
                .single();
    }

    @AfterEach
    void limpar() {
        // As tabelas de auth sao limpas explicitamente, sem confiar na cascata — a cascata
        // tem teste proprio. Inclui o admin semeado, que um dos testes usa para entrar.
        for (String tabela : List.of("refresh_tokens", "login_lockouts")) {
            jdbc.sql("DELETE FROM " + tabela + """
                     WHERE user_id IN (SELECT id FROM users
                                       WHERE email LIKE :prefixo OR email = 'admin@ticketsystem.local')
                    """)
                    .param("prefixo", PREFIXO + "%")
                    .update();
        }
        jdbc.sql("DELETE FROM users WHERE email LIKE :prefixo").param("prefixo", PREFIXO + "%").update();
    }

    // ------------------------------------------------------------------- login

    @Test
    @DisplayName("o admin semeado entra com a senha que o README publica, e o token serve")
    void adminSemeadoEntra() throws Exception {
        MvcResult resposta = login("admin@ticketsystem.local", "admin123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(1800))
                .andReturn();

        long idDoAdmin = jdbc.sql("SELECT id FROM users WHERE email = 'admin@ticketsystem.local'")
                .query(Long.class).single();
        // Fecha o ciclo: o token emitido pelo login e aceito pela cadeia de filtros. Prova
        // que quem assina e quem valida usam a mesma chave, com a configuracao real.
        mvc.perform(get("/api/v1/users/" + idDoAdmin)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + campo(resposta, "$.accessToken")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("senha errada e e-mail inexistente recebem exatamente a mesma resposta")
    void senhaErradaEEmailInexistenteSaoIndistinguiveis() throws Exception {
        String senhaErrada = login(email, "senha-errada").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String contaInexistente = login(PREFIXO + "ninguem@exemplo.com", "qualquer")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // Mesmo status, mesmo corpo. Qualquer diferenca transformaria o login num
        // verificador de quais e-mails tem conta.
        assertThat(senhaErrada).isEqualTo(contaInexistente);
        assertThat(JsonPath.<String>read(senhaErrada, "$.detail")).isEqualTo("E-mail ou senha incorretos.");
    }

    @Test
    @DisplayName("uma unica senha errada ja fica gravada no banco")
    void umaFalhaFicaGravada() throws Exception {
        login(email, "senha-errada").andExpect(status().isUnauthorized());

        // O teste que pega o maior risco da fase. O login lanca excecao logo depois de
        // contar a falha; se a contagem participasse da transacao dele, o rollback a
        // desfaria. Afirmar "a quarta tentativa falhou" nao provaria nada — senha errada
        // falha de qualquer jeito. So o banco diz se a falha foi contada.
        assertThat(falhasRegistradas()).isOne();
    }

    @Test
    @DisplayName("depois do limite de falhas, nem a senha certa entra")
    void bloqueioRecusaASenhaCerta() throws Exception {
        for (int i = 0; i < 3; i++) {
            login(email, "senha-errada").andExpect(status().isUnauthorized());
        }

        // A tentativa com a senha CERTA e o coracao do teste: e a unica forma de
        // distinguir "bloqueado" de "credencial invalida".
        login(email, SENHA)
                .andExpect(status().isLocked())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(423));
    }

    @Test
    @DisplayName("quando o bloqueio vence, a senha certa volta a entrar e o historico e apagado")
    void bloqueioVencidoLibera() throws Exception {
        for (int i = 0; i < 3; i++) {
            login(email, "senha-errada");
        }
        // "O bloqueio venceu" como estado, e nao como espera: sem sleep, sem relogio falso.
        jdbc.sql("UPDATE login_lockouts SET locked_until = :passado WHERE user_id = :usuario")
                .param("passado", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1))
                .param("usuario", usuario)
                .update();

        login(email, SENHA).andExpect(status().isOk());

        assertThat(falhasRegistradas()).isNull();
    }

    @Test
    @DisplayName("e-mail malformado e 400 apontando o campo")
    void emailMalformadoEh400() throws Exception {
        login("nao-e-email", "qualquer")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    // ------------------------------------------------------------- sessao

    @Test
    @DisplayName("renovar troca o refresh token e entrega um access token que funciona")
    void renovarTrocaOToken() throws Exception {
        String primeiro = campo(login(email, SENHA).andReturn(), "$.refreshToken");

        MvcResult renovado = refresh(primeiro).andExpect(status().isOk()).andReturn();

        assertThat(campo(renovado, "$.refreshToken")).isNotEqualTo(primeiro);
        assertThat(campo(renovado, "$.accessToken")).isNotBlank();
    }

    @Test
    @DisplayName("reusar um refresh token ja trocado derruba a sessao inteira, inclusive o token novo")
    void reusoDerrubaASessao() throws Exception {
        String primeiro = campo(login(email, SENHA).andReturn(), "$.refreshToken");
        String segundo = campo(refresh(primeiro).andReturn(), "$.refreshToken");

        // Alguem guardou uma copia do primeiro token e tenta usa-la.
        refresh(primeiro).andExpect(status().isUnauthorized());

        // A assercao que importa: o token LEGITIMO mais recente tambem morreu. "O replay deu
        // 401" passaria mesmo sem revogar familia nenhuma, so por causa do used_at — e o
        // segundo token, que numa situacao real estaria na mao do ladrao, continuaria vivo.
        refresh(segundo).andExpect(status().isUnauthorized());

        // E a revogacao sobreviveu a excecao lancada logo depois dela (noRollbackFor).
        assertThat(motivosDeRevogacao()).containsOnly("REUSE_DETECTED");
    }

    @Test
    @DisplayName("o banco guarda o hash do refresh token, nunca o valor entregue")
    void tokenCruNaoEhGravado() throws Exception {
        String entregue = campo(login(email, SENHA).andReturn(), "$.refreshToken");

        Long comValorCru = jdbc.sql("SELECT count(*) FROM refresh_tokens WHERE token_hash = :valor")
                .param("valor", entregue).query(Long.class).single();
        Long comHash = jdbc.sql("SELECT count(*) FROM refresh_tokens WHERE token_hash = :hash")
                .param("hash", sha256(entregue)).query(Long.class).single();

        assertThat(comValorCru).isZero();
        assertThat(comHash).isOne();
    }

    @Test
    @DisplayName("depois do logout, o refresh token nao renova mais")
    void logoutEncerraASessao() throws Exception {
        String token = campo(login(email, SENHA).andReturn(), "$.refreshToken");

        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(token)))
                .andExpect(status().isNoContent());

        refresh(token).andExpect(status().isUnauthorized());
        assertThat(motivosDeRevogacao()).containsOnly("LOGOUT");
    }

    @Test
    @DisplayName("logout com token desconhecido responde igual, sem dizer que nao o conhecia")
    void logoutDesconhecidoResponde204() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"nunca-existiu\"}"))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------ apoio

    private ResultActions login(String emailDoLogin, String senha) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(emailDoLogin, senha)));
    }

    private ResultActions refresh(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(token)));
    }

    private Integer falhasRegistradas() {
        return jdbc.sql("SELECT failed_attempts FROM login_lockouts WHERE user_id = :usuario")
                .param("usuario", usuario).query(Integer.class).optional().orElse(null);
    }

    private List<String> motivosDeRevogacao() {
        return jdbc.sql("SELECT revoked_reason FROM refresh_tokens WHERE user_id = :usuario")
                .param("usuario", usuario).query(String.class).list();
    }

    private static String campo(MvcResult resposta, String caminho) throws Exception {
        return JsonPath.read(resposta.getResponse().getContentAsString(), caminho);
    }

    private static String sha256(String valor) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
    }
}
