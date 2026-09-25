package com.ticketsystem.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JpaConfigTest {

    @Test
    @DisplayName("o carimbo de auditoria tem a precisao que o Postgres guarda: microssegundos")
    void carimboEmMicrossegundos() {
        // O relogio do Windows tem resolucao de 100ns; o Postgres guarda microssegundos. Sem
        // truncar, a resposta do POST traz um createdAt que o GET seguinte ja nao devolve.
        Clock relogio = Clock.fixed(Instant.parse("2026-09-25T12:00:00.123456789Z"), ZoneOffset.UTC);

        assertThat(new JpaConfig().auditingDateTimeProvider(relogio).getNow())
                .contains(Instant.parse("2026-09-25T12:00:00.123456Z"));
    }
}
