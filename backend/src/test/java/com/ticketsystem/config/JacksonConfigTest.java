package com.ticketsystem.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

/**
 * O formato de data e contrato publicado: o frontend parseia ISO-8601 e um numero no
 * lugar quebraria a tela sem quebrar compilacao nenhuma.
 *
 * <p>Este teste existe para nao depender do default do Jackson 3 — se uma versao futura
 * voltar a escrever timestamp, ele quebra aqui e nao em producao.
 */
class JacksonConfigTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
        .withUserConfiguration(JacksonConfig.class);

    @Test
    void serializaInstantComoStringIso8601() {
        contexto.run(ctx -> {
            ObjectMapper mapper = ctx.getBean(ObjectMapper.class);

            String json = mapper.writeValueAsString(new ComPrazo(Instant.parse("2026-09-19T14:30:00Z")));

            assertThat(json).isEqualTo("{\"prazo\":\"2026-09-19T14:30:00Z\"}");
        });
    }

    private record ComPrazo(Instant prazo) {
    }
}
