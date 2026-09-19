package com.ticketsystem;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketsystem.support.IntegrationTest;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prova que o contexto sobe de ponta a ponta: Spring, Security, JPA e Flyway contra um
 * Postgres de verdade.
 */
@IntegrationTest
class TicketSystemApplicationIT {

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void oContextoSobeContraUmPostgresReal() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        }
    }

    /**
     * O Flyway falha em silencio quando falta o modulo de autoconfiguracao: a aplicacao
     * sobe, nao loga nada e nao migra. A tabela de historico e a unica evidencia de que
     * ele realmente rodou, e por isso ela e verificada aqui e nao presumida.
     */
    @Test
    void oFlywayRodaEDeixaARastreabilidadeDoSchema() {
        Boolean existe = jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'flyway_schema_history')",
            Boolean.class);

        assertThat(existe)
            .as("tabela de historico do Flyway; sem ela nenhuma migration foi aplicada")
            .isTrue();
    }
}
