package com.ticketsystem.auth.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketsystem.auth.CurrentUser;
import com.ticketsystem.auth.service.NotAuthenticatedException;
import com.ticketsystem.common.error.ProblemKind;
import com.ticketsystem.user.UserRole;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Quem esta pedindo, lido do que a cadeia de seguranca ja validou.
 *
 * <p>Qualquer duvida sobre a identidade vira 401, e nunca um usuario "mais ou menos"
 * resolvido: e daqui que sai o id que as regras de equipe — e, na Fase 5, as de
 * visibilidade — comparam com o banco.
 */
class JwtCurrentUserResolverTest {

    private final JwtCurrentUserResolver resolver = new JwtCurrentUserResolver();

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("id e papel saem do token autenticado")
    void idEPapelSaemDoToken() {
        autenticar("42", List.of(new SimpleGrantedAuthority("ROLE_AGENT")));

        assertThat(resolver.resolve()).isEqualTo(new CurrentUser(42L, UserRole.AGENT));
    }

    @Test
    @DisplayName("sem autenticacao no contexto e 401, nao nulo")
    void semAutenticacaoEh401() {
        assertThatThrownBy(resolver::resolve)
                .isInstanceOf(NotAuthenticatedException.class)
                .extracting(e -> ((NotAuthenticatedException) e).kind())
                .isEqualTo(ProblemKind.UNAUTHORIZED);
    }

    @Test
    @DisplayName("autenticacao anonima nao vira usuario")
    void anonimoEh401() {
        // O filtro de anonimo do Spring poe um Authentication no contexto de toda rota
        // publica. "Tem alguem no contexto" nao e o mesmo que "sabemos quem e".
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "chave", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThatThrownBy(resolver::resolve).isInstanceOf(NotAuthenticatedException.class);
    }

    @Test
    @DisplayName("subject que nao e id numerico e 401")
    void subjectNaoNumericoEh401() {
        autenticar("ana@empresa.com", List.of(new SimpleGrantedAuthority("ROLE_AGENT")));

        assertThatThrownBy(resolver::resolve).isInstanceOf(NotAuthenticatedException.class);
    }

    @ParameterizedTest(name = "authority {0}")
    @ValueSource(strings = {"AGENT", "ROLE_SUPERUSER", "SCOPE_read"})
    @DisplayName("sem um papel conhecido no formato ROLE_* e 401")
    void papelDesconhecidoEh401(String authority) {
        // Um papel que nao casa com UserRole nao pode cair num default: qualquer default
        // escolhido aqui daria a alguem permissao que o token nao concedeu.
        autenticar("42", List.of(new SimpleGrantedAuthority(authority)));

        assertThatThrownBy(resolver::resolve).isInstanceOf(NotAuthenticatedException.class);
    }

    @Test
    @DisplayName("dois papeis no mesmo token e 401, e nao o primeiro que aparecer")
    void doisPapeisEh401() {
        // O login emite um papel so. Dois significam token que nao saiu daqui, e escolher
        // um deles seria decidir permissao pela ordem de uma lista.
        autenticar("42", List.of(new SimpleGrantedAuthority("ROLE_AGENT"), new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertThatThrownBy(resolver::resolve).isInstanceOf(NotAuthenticatedException.class);
    }

    @Test
    @DisplayName("so ADMIN e admin")
    void soAdminEhAdmin() {
        assertThat(new CurrentUser(1L, UserRole.ADMIN).isAdmin()).isTrue();
        assertThat(new CurrentUser(1L, UserRole.AGENT).isAdmin()).isFalse();
        assertThat(new CurrentUser(1L, UserRole.REQUESTER).isAdmin()).isFalse();
    }

    private static void autenticar(String subject, List<? extends GrantedAuthority> permissoes) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        Authentication autenticacao = new JwtAuthenticationToken(jwt, permissoes);
        SecurityContextHolder.getContext().setAuthentication(autenticacao);
    }
}
