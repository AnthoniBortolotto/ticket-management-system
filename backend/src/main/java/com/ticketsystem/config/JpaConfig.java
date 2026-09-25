package com.ticketsystem.config;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Liga o preenchimento automatico de {@code createdAt} e {@code updatedAt} em
 * {@link com.ticketsystem.common.domain.BaseEntity}.
 *
 * <p>O instante vem do {@link Clock} da aplicacao, truncado em microssegundos — a precisao de
 * {@code TIMESTAMP WITH TIME ZONE} no Postgres. Sem o truncamento, o valor em memoria tem
 * nanossegundos (no Windows, 100ns), a resposta do {@code POST} devolve esse valor, e o
 * {@code GET} seguinte devolve o gravado: o mesmo recurso com dois {@code createdAt}.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
class JpaConfig {

    @Bean
    DateTimeProvider auditingDateTimeProvider(Clock relogio) {
        return () -> Optional.of(relogio.instant().truncatedTo(ChronoUnit.MICROS));
    }
}
