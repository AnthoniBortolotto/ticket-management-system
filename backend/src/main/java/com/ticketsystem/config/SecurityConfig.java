package com.ticketsystem.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Cadeia de filtros da API: quem pode chamar o que, e como uma recusa e respondida.
 *
 * <p>Stateless: nao ha sessao de servidor nem cookie. Cada requisicao traz um JWT no
 * cabecalho {@code Authorization: Bearer}, validado pelo resource server do Spring
 * Security — assinatura, algoritmo e validade, sem uma linha de validacao escrita a mao.
 *
 * <p>CSRF desligado porque nao ha credencial que o navegador envie sozinho: o token vai
 * num cabecalho, e o browser nunca fala com esta API direto — quem chama e o servidor do
 * Next.js, que guarda o token em cookie {@code httpOnly} do lado de la.
 *
 * <p>O padrao e negar. Toda rota nao listada abaixo exige autenticacao, entao um endpoint
 * novo nasce protegido, e liberar e que exige uma decisao escrita.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    /**
     * Sonda de saude e documentacao do contrato.
     *
     * <p>{@code /swagger-ui} e {@code /v3/api-docs} ficam publicos por decisao, registrada
     * no ADR 0003: e projeto de portfolio, o contrato navegavel sem token e parte da
     * demonstracao, e ele nao expoe dado nenhum — so o formato das requisicoes.
     */
    static final String[] PUBLIC_PATHS = {
        "/actuator/health",
        "/actuator/health/**",
        "/v3/api-docs",
        "/v3/api-docs/**",
        "/swagger-ui.html",
        "/swagger-ui/**"
    };

    /**
     * Os tres endpoints de sessao, listados um a um e so para {@code POST}.
     *
     * <p>Nunca como {@code /api/v1/auth/**}: esse curinga liberaria, sem ninguem perceber,
     * todo endpoint que o modulo ganhasse depois — e os testes de "rota publica responde"
     * continuariam verdes. {@code refresh} e {@code logout} sao publicos porque quem os
     * chama pode estar com o access token ja expirado; a credencial ali e o refresh token
     * no corpo.
     */
    static final String[] SESSION_PATHS = {
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout"
    };

    @Bean
    SecurityFilterChain apiFilterChain(
            HttpSecurity http,
            Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
            AuthenticationEntryPoint problemAuthenticationEntryPoint,
            AccessDeniedHandler problemAccessDeniedHandler) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(requests -> requests
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .requestMatchers(HttpMethod.POST, SESSION_PATHS).permitAll()
                .requestMatchers("/api/v1/users", "/api/v1/users/**").hasRole("ADMIN")
                // Roteamento de categoria para equipe e configuracao do sistema, e a regra
                // e so de papel. Tickets e equipes nao aparecem aqui: a regra deles depende
                // do recurso, e mora inteira no service de cada modulo.
                .requestMatchers("/api/v1/ticket-routes", "/api/v1/ticket-routes/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(resourceServer -> resourceServer
                // O conversor vai explicito: e ele que transforma o claim `role` em
                // ROLE_ADMIN. Sem ele, todo mundo autentica com zero permissoes e recebe
                // 403 em tudo — bug de conversao disfarcado de bug de permissao.
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                .authenticationEntryPoint(problemAuthenticationEntryPoint)
                .accessDeniedHandler(problemAccessDeniedHandler))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(problemAuthenticationEntryPoint)
                .accessDeniedHandler(problemAccessDeniedHandler))
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .build();
    }

    /**
     * Resposta a "nao sei quem voce e" — token ausente, invalido ou expirado.
     *
     * <p>401 nasce dentro da cadeia de filtros, antes do {@code DispatcherServlet}, e por
     * isso nao passaria pelo {@code @RestControllerAdvice}: a API teria um formato de erro
     * para o dominio e outro para autenticacao. Devolver a excecao ao
     * {@link HandlerExceptionResolver} — o mesmo objeto que alimenta o advice — e o que
     * faz 401 sair em {@code ProblemDetail} como todo o resto.
     *
     * <p>O cabecalho {@code WWW-Authenticate} vai na mao porque substituir o entry point
     * padrao do resource server tira o que ele poria.
     */
    @Bean
    AuthenticationEntryPoint problemAuthenticationEntryPoint(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        return (request, response, exception) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            delegarAoAdvice(resolver, request, response, exception, HttpStatus.UNAUTHORIZED);
        };
    }

    /** Resposta a "sei quem voce e, e voce nao pode" — mesmo caminho, 403. */
    @Bean
    AccessDeniedHandler problemAccessDeniedHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        return (request, response, exception) ->
                delegarAoAdvice(resolver, request, response, exception, HttpStatus.FORBIDDEN);
    }

    /**
     * Se nenhum {@code @ExceptionHandler} reconhecer a excecao, {@code resolveException}
     * devolve {@code null} e a resposta sairia <strong>200 com corpo vazio</strong> — uma
     * requisicao sem token virando "sucesso", sem erro nem log. O {@code sendError} abaixo
     * torna esse caso impossivel: no pior cenario sai o status certo sem o corpo padrao.
     */
    private static void delegarAoAdvice(
            HandlerExceptionResolver resolver,
            HttpServletRequest request,
            HttpServletResponse response,
            Exception exception,
            HttpStatus statusDeSeguranca) throws IOException {
        if (resolver.resolveException(request, response, null, exception) == null) {
            response.sendError(statusDeSeguranca.value());
        }
    }

    /**
     * Como as senhas sao verificadas e gravadas.
     *
     * <p><strong>BCrypt puro, e nao {@code PasswordEncoderFactories.createDelegatingPasswordEncoder()}.</strong>
     * O delegating grava com prefixo de algoritmo ({@code {bcrypt}$2a$10$...}), e isso
     * quebra o sistema em dois lugares ao mesmo tempo: o valor com prefixo viola o CHECK
     * {@code users_password_hash_is_bcrypt} da migration V2, entao nenhum usuario novo
     * seria gravado; e o {@code matches} contra o hash pelado do admin semeado falharia por
     * nao achar prefixo, entao ninguem conseguiria entrar. Compila e sobe normalmente — e
     * por isso existe teste so para esta escolha.
     *
     * <p>O custo fica no padrao da biblioteca (10). Subi-lo e mudanca de seguranca legitima,
     * mas hashes antigos continuam validando com o custo com que foram gerados.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
