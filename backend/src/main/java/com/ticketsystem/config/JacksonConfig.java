package com.ticketsystem.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.DateTimeFeature;

/**
 * Serializacao de datas da API.
 *
 * <p>Tudo e persistido em {@link java.time.Instant} UTC e atravessa o HTTP como string
 * ISO-8601; converter para o fuso de quem le e responsabilidade do frontend.
 *
 * <p>Por que isto e uma classe e nao uma linha em {@code application.yml}: no Jackson 3
 * a flag {@code WRITE_DATES_AS_TIMESTAMPS} saiu de {@code SerializationFeature} e foi
 * para {@link DateTimeFeature}, e o Spring Boot 4 nao expoe propriedade para esse grupo
 * — {@code spring.jackson.serialization.write-dates-as-timestamps} nao existe mais e
 * derruba o contexto no boot. Como o formato da data e contrato publicado, ele fica
 * explicito aqui em vez de depender do default da biblioteca.
 */
@Configuration
class JacksonConfig {

    @Bean
    JsonMapperBuilderCustomizer datasEmIso8601() {
        return builder -> builder.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
