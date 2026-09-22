package com.ticketsystem.support;

import com.ticketsystem.user.UserRole;
import com.ticketsystem.user.domain.User;
import jakarta.persistence.EntityManager;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Monta usuarios de teste em uma linha.
 *
 * <p>A partir da Fase 5 cada cenario de visibilidade precisa de cinco ou seis pessoas com
 * papeis diferentes. Sem isto, o setup de cada teste vira quarenta linhas ilegiveis e as
 * pessoas param de escrever teste — que e exatamente o que o CLAUDE.md quer evitar.
 *
 * <p>Nao tem dependencia de Spring: {@link #build()} serve aos unitarios de {@code domain}
 * e {@code service}, que sao onde o threshold de cobertura mora, e {@link #persistIn} serve
 * aos testes de integracao.
 *
 * <p><strong>Nao conhece equipe.</strong> Vinculo e conceito do modulo {@code team} e
 * pertence ao {@code TeamBuilder}, na Fase 3. O Modulith nao analisa codigo de teste, entao
 * {@code support} e justamente onde a fronteira entre modulos se dissolveria sem ninguem
 * reclamar.
 */
public final class UserBuilder {

    /** Prefixo de limpeza: os testes apagam por ele no {@code @AfterEach}. */
    public static final String PREFIXO = "builder-";

    /**
     * Hash BCrypt de "senha-de-teste".
     *
     * <p>Constante de proposito: gerar um BCrypt de custo 10 leva ~80ms, e vinte usuarios
     * num teste de integracao seriam 1,6s jogados fora. Quem precisa de uma senha que
     * realmente confira usa {@link #withPassword(String)}.
     */
    private static final String HASH_PADRAO =
            "$2a$10$TqZk9EsL91XXwsirPJRFpugEf.AsiMXF2h.Pr1z1FQ4GjfCwiHpBC";

    private static final AtomicLong SEQUENCIA = new AtomicLong();

    private String email;
    private String fullName = "Pessoa de Teste";
    private String passwordHash = HASH_PADRAO;
    private UserRole role = UserRole.AGENT;

    private UserBuilder(UserRole role) {
        this.role = role;
        long n = SEQUENCIA.incrementAndGet();
        // Unico por padrao, e minusculo: a coluna tem UNIQUE e CHECK (email = lower(email)),
        // entao um e-mail fixo estouraria no segundo usuario do mesmo teste.
        this.email = PREFIXO + role.name().toLowerCase(java.util.Locale.ROOT) + n + "@exemplo.com";
    }

    public static UserBuilder aUser() {
        return new UserBuilder(UserRole.AGENT);
    }

    public static UserBuilder aRequester() {
        return new UserBuilder(UserRole.REQUESTER);
    }

    public static UserBuilder anAgent() {
        return new UserBuilder(UserRole.AGENT);
    }

    public static UserBuilder anAdmin() {
        return new UserBuilder(UserRole.ADMIN);
    }

    public UserBuilder withEmail(String email) {
        this.email = email;
        return this;
    }

    public UserBuilder withFullName(String fullName) {
        this.fullName = fullName;
        return this;
    }

    public UserBuilder withRole(UserRole role) {
        this.role = role;
        return this;
    }

    /** Codifica de verdade. Use so quando o teste precisa que a senha confira. */
    public UserBuilder withPassword(String rawPassword) {
        this.passwordHash = new BCryptPasswordEncoder().encode(rawPassword);
        return this;
    }

    /**
     * Um usuario em memoria, com id sintetico.
     *
     * <p>O id importa mais do que parece: {@code BaseEntity.equals} devolve {@code false}
     * quando o id e nulo, entao duas instancias sem id nunca sao iguais. Uma politica de
     * acesso que compare {@code ticket.solicitante().equals(usuario)} responderia "nao pode
     * ver" ate para o proprio solicitante, e o teste do caso negativo passaria por acidente
     * — verde e sem valor nenhum.
     */
    public User build() {
        User usuario = new User(email, fullName, passwordHash, role);
        ReflectionTestUtils.setField(usuario, "id", SEQUENCIA.incrementAndGet());
        return usuario;
    }

    /**
     * Grava de verdade e devolve a instancia gerenciada.
     *
     * <p>Sem id sintetico aqui: quem atribui e a sequencia do banco.
     */
    public User persistIn(EntityManager em) {
        User usuario = new User(email, fullName, passwordHash, role);
        em.persist(usuario);
        em.flush();
        return usuario;
    }
}
