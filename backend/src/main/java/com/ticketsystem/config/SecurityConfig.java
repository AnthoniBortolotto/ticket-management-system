package com.ticketsystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cadeia de filtros da API.
 *
 * <p>A API e stateless: nao ha sessao nem CSRF token, porque o cliente se identifica
 * por JWT a cada requisicao. Enquanto o modulo {@code auth} nao existe, nenhuma rota
 * protegida e alcancavel — o padrao e negar, nao liberar.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    /** Rotas abertas: sonda de saude e a documentacao do contrato. */
    private static final String[] PUBLIC_PATHS = {
        "/actuator/health",
        "/actuator/health/**",
        "/v3/api-docs",
        "/v3/api-docs/**",
        "/swagger-ui.html",
        "/swagger-ui/**"
    };

    @Bean
    SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(requests -> requests
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .anyRequest().authenticated())
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .build();
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
