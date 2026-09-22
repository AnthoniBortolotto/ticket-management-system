package com.ticketsystem.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * O relogio da aplicacao, como bean.
 *
 * <p>Existe para que nenhum service precise chamar {@code Instant.now()} direto. Com o
 * relogio injetado, testar expiracao de token ou fim de bloqueio e substituir um
 * {@code Clock.fixed} — sem {@code Thread.sleep}, que deixa a suite lenta e intermitente.
 *
 * <p>UTC, como todo o resto do sistema: converter para o fuso de quem le e
 * responsabilidade do frontend.
 */
@Configuration
class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
