package com.ticketsystem.user.web.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketsystem.support.AuthTokens;
import com.ticketsystem.support.IntegrationTest;
import com.ticketsystem.support.SecureMockMvc;
import com.ticketsystem.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

/**
 * Gestao de usuarios pelo endpoint, com a cadeia de seguranca e o banco reais.
 *
 * <p>O caso negativo de permissao — quem nao e admin nao cria usuario — e item obrigatorio
 * da definition of done do CLAUDE.md, e esta aqui com token de verdade.
 */
@IntegrationTest
class UserControllerIT {

    private static final String PREFIXO = "user-it-";

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mvc;
    private AuthTokens tokens;
    private long idDoAdmin;

    @BeforeEach
    void configurar() {
        mvc = SecureMockMvc.from(contexto);
        tokens = new AuthTokens(encoder);
        idDoAdmin = jdbc.sql("SELECT id FROM users WHERE email = 'admin@ticketsystem.local'")
                .query(Long.class).single();
    }

    @AfterEach
    void limpar() {
        jdbc.sql("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE email LIKE :p)")
                .param("p", PREFIXO + "%").update();
        jdbc.sql("DELETE FROM users WHERE email LIKE :p").param("p", PREFIXO + "%").update();
    }

    @Test
    @DisplayName("admin cria usuario, e a pessoa criada consegue entrar")
    void adminCriaUsuarioQueConsegueEntrar() throws Exception {
        criarComo(UserRole.ADMIN, PREFIXO + "nova@exemplo.com", "Nova Pessoa", "senha-da-nova", "AGENT")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(PREFIXO + "nova@exemplo.com"))
                .andExpect(jsonPath("$.role").value("AGENT"))
                // Nem o hash nem nada parecido com senha sai na resposta.
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        // O fim a fim que importa: o hash gerado pelo encoder do contexto passou pelo CHECK
        // de BCrypt da V2 e confere no login. Com o encoder "delegating", o INSERT seria
        // recusado — ou, se passasse, o login falharia.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"senha-da-nova\"}"
                                .formatted(PREFIXO + "nova@exemplo.com")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("e-mail com maiuscula e guardado em minusculas")
    void emailEhNormalizado() throws Exception {
        criarComo(UserRole.ADMIN, PREFIXO.toUpperCase() + "Maiuscula@Exemplo.COM", "Pessoa", "senha-longa-ok", "AGENT")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(PREFIXO + "maiuscula@exemplo.com"));
    }

    @Test
    @DisplayName("e-mail ja cadastrado e 409, sem criar duplicata")
    void emailDuplicadoEh409() throws Exception {
        criarComo(UserRole.ADMIN, PREFIXO + "repetida@exemplo.com", "Um", "senha-longa-ok", "AGENT");

        criarComo(UserRole.ADMIN, PREFIXO + "REPETIDA@exemplo.com", "Dois", "senha-longa-ok", "AGENT")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("senha acentuada que passa de 72 bytes e 400, e nao erro interno")
    void senhaLongaEmBytesEh400() throws Exception {
        // 40 caracteres passam no @Size(max = 72) do DTO, mas sao 80 bytes — e o BCrypt
        // recusaria com excecao, que chegaria ao cliente como 500.
        criarComo(UserRole.ADMIN, PREFIXO + "acento@exemplo.com", "Pessoa", "ç".repeat(40), "AGENT")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Senha longa demais"));
    }

    @Test
    @DisplayName("nome no limite de 150 caracteres entra; 151 e recusado antes do banco")
    void larguraDoNomeEhContrato() throws Exception {
        // O ddl-auto: validate nao confere largura contra Postgres. Este par e o que prova
        // que DTO, entidade e coluna concordam no limite.
        criarComo(UserRole.ADMIN, PREFIXO + "longo@exemplo.com", "n".repeat(150), "senha-longa-ok", "AGENT")
                .andExpect(status().isCreated());
        criarComo(UserRole.ADMIN, PREFIXO + "longo2@exemplo.com", "n".repeat(151), "senha-longa-ok", "AGENT")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("fullName"));
    }

    @ParameterizedTest(name = "{0} nao cria usuario")
    @ValueSource(strings = {"AGENT", "REQUESTER"})
    @DisplayName("quem nao e admin nao cria usuario")
    void naoAdminNaoCriaUsuario(UserRole papel) throws Exception {
        criarComo(papel, PREFIXO + "intrusa@exemplo.com", "Intrusa", "senha-longa-ok", "ADMIN")
                .andExpect(status().isForbidden());

        // E nada foi gravado: a recusa aconteceu antes do controller.
        assertThat(jdbc.sql("SELECT count(*) FROM users WHERE email = :e")
                        .param("e", PREFIXO + "intrusa@exemplo.com").query(Long.class).single())
                .isZero();
    }

    @Test
    @DisplayName("id que nao existe e 404")
    void idInexistenteEh404() throws Exception {
        mvc.perform(get("/api/v1/users/999999999").header(HttpHeaders.AUTHORIZATION, bearer(UserRole.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("o encoder de senha do contexto e BCrypt puro, sem prefixo de algoritmo")
    void encoderEhBcryptPuro() {
        // O "delegating" gravaria {bcrypt}$2a$..., que o CHECK da V2 recusa e que nao
        // confere com o hash pelado do admin semeado. Compila e sobe; so quebraria em uso.
        assertThat(passwordEncoder.encode("qualquer")).startsWith("$2");
        String hashDoAdmin = jdbc.sql("SELECT password_hash FROM users WHERE id = :id")
                .param("id", idDoAdmin).query(String.class).single();
        assertThat(passwordEncoder.matches("admin123", hashDoAdmin)).isTrue();
    }

    private ResultActions criarComo(UserRole papel, String email, String nome, String senha, String papelNovo)
            throws Exception {
        return mvc.perform(post("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(papel))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"fullName\":\"%s\",\"password\":\"%s\",\"role\":\"%s\"}"
                        .formatted(email, nome, senha, papelNovo)));
    }

    private String bearer(UserRole papel) {
        return "Bearer " + tokens.valid(idDoAdmin, papel);
    }
}
