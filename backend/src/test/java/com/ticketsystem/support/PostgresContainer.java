package com.ticketsystem.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Postgres real para os testes de integracao — nunca H2, porque o schema usa recursos
 * especificos do Postgres e um banco diferente nos testes so prova que o teste passa.
 *
 * <p>O container e reaproveitado entre execucoes: com
 * {@code testcontainers.reuse.enable=true} no ~/.testcontainers.properties, o startup
 * e pago uma vez, nao a cada rodada.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainer {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine").withReuse(true);
    }
}
