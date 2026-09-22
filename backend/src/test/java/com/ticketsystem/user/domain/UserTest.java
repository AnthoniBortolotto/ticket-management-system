package com.ticketsystem.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketsystem.user.UserRole;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** As invariantes do usuario, sem Spring e sem banco. */
class UserTest {

    private static final String HASH =
            "$2a$10$TqZk9EsL91XXwsirPJRFpugEf.AsiMXF2h.Pr1z1FQ4GjfCwiHpBC";

    @Test
    @DisplayName("o e-mail e guardado em minusculas")
    void emailEhNormalizadoParaMinusculas() {
        var usuario = new User("Ana.Ribeiro@Empresa.COM", "Ana Ribeiro", HASH, UserRole.AGENT);

        // A coluna tem CHECK (email = lower(email)): normalizar aqui e o que impede o
        // INSERT ser recusado em runtime, e o que faz 'Ana@x' e 'ana@x' serem a mesma
        // conta em vez de duas visibilidades sobre os mesmos tickets.
        assertThat(usuario.getEmail()).isEqualTo("ana.ribeiro@empresa.com");
    }

    @Test
    @DisplayName("espaco em volta do e-mail nao vira parte do e-mail")
    void emailEhAparado() {
        var usuario = new User("  ana@empresa.com  ", "Ana", HASH, UserRole.REQUESTER);

        assertThat(usuario.getEmail()).isEqualTo("ana@empresa.com");
    }

    @Test
    @DisplayName("a normalizacao nao depende do idioma da maquina")
    void normalizacaoNaoDependeDoLocale() {
        Locale anterior = Locale.getDefault();
        try {
            // Em turco, lower('I') e 'i' sem ponto — 'ADMIN@X' viraria 'admın@x' e a conta
            // simplesmente nao seria encontrada no login. E o tipo de bug que so aparece
            // na maquina de outra pessoa.
            Locale.setDefault(Locale.forLanguageTag("tr"));

            var usuario = new User("ADMIN@EMPRESA.COM", "Admin", HASH, UserRole.ADMIN);

            assertThat(usuario.getEmail()).isEqualTo("admin@empresa.com");
        } finally {
            Locale.setDefault(anterior);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("e-mail vazio nao cria usuario")
    void emailVazioEhRejeitado(String email) {
        assertThatThrownBy(() -> new User(email, "Ana", HASH, UserRole.AGENT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("e-mail nulo nao cria usuario")
    void emailNuloEhRejeitado() {
        assertThatThrownBy(() -> new User(null, "Ana", HASH, UserRole.AGENT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("nome vazio nao cria usuario")
    void nomeVazioEhRejeitado(String nome) {
        assertThatThrownBy(() -> new User("ana@empresa.com", nome, HASH, UserRole.AGENT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("hash de senha vazio nao cria usuario")
    void hashVazioEhRejeitado() {
        assertThatThrownBy(() -> new User("ana@empresa.com", "Ana", "  ", UserRole.AGENT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("papel nulo nao cria usuario")
    void papelNuloEhRejeitado() {
        assertThatThrownBy(() -> new User("ana@empresa.com", "Ana", HASH, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("o hash da senha nunca aparece no toString")
    void hashNaoVazaNoToString() {
        var usuario = new User("ana@empresa.com", "Ana", HASH, UserRole.AGENT);

        // toString de entidade acaba em log de erro e em mensagem de excecao. Um hash ali
        // e credencial exposta em texto para quem tiver acesso ao log.
        assertThat(usuario.toString()).doesNotContain(HASH).contains("ana@empresa.com");
    }

    @Test
    @DisplayName("trocar a senha substitui o hash")
    void trocarSenhaSubstituiOHash() {
        var usuario = new User("ana@empresa.com", "Ana", HASH, UserRole.AGENT);
        String novo = "$2a$10$qKPdARdr8P.zVL8vWjLuIuuVew89Vd38WEBlkyecf5FAmxBk.54IS";

        usuario.changePasswordHash(novo);

        assertThat(usuario.getPasswordHash()).isEqualTo(novo);
    }

    @Test
    @DisplayName("trocar a senha por vazio nao e permitido")
    void trocarSenhaPorVazioEhRejeitado() {
        var usuario = new User("ana@empresa.com", "Ana", HASH, UserRole.AGENT);

        assertThatThrownBy(() -> usuario.changePasswordHash("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
