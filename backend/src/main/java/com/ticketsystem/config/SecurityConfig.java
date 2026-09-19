package com.ticketsystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
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
}
