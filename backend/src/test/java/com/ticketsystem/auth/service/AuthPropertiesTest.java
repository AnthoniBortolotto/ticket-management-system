package com.ticketsystem.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A configuracao de login falha no boot quando esta errada, em vez de no primeiro login.
 *
 * <p>Cada rejeicao aqui existe por um motivo concreto. A mais importante e a do texto
 * literal {@code ${JWT_SECRET}}: o Spring nao reclama de variavel de ambiente ausente —
 * ele deixa o placeholder como valor, e sem esta checagem a aplicacao subiria assinando
 * tokens com essa string publica como chave. E a mesma armadilha que quase semeou o admin
 * com a senha {@code ${ADMIN_PASSWORD_HASH}} na Fase 1.
 */
class AuthPropertiesTest {

    private static final String SEGREDO_VALIDO = "um-segredo-de-teste-com-mais-de-32-bytes-de-tamanho";
    private static final Duration TRINTA_MINUTOS = Duration.ofMinutes(30);
    private static final Duration SETE_DIAS = Duration.ofDays(7);
    private static final Duration QUINZE_MINUTOS = Duration.ofMinutes(15);

    @Test
    @DisplayName("configuracao completa e aceita")
    void configuracaoCompletaEhAceita() {
        assertThatCode(() -> comSegredo(SEGREDO_VALIDO)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "segredo \"{0}\" e recusado")
    @ValueSource(strings = {"", "   "})
    @DisplayName("segredo vazio nao sobe")
    void segredoVazioEhRecusado(String segredo) {
        assertThatThrownBy(() -> comSegredo(segredo)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("segredo ausente nao sobe")
    void segredoNuloEhRecusado() {
        assertThatThrownBy(() -> comSegredo(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("o placeholder nao resolvido e recusado pelo nome")
    void placeholderNaoResolvidoEhRecusado() {
        // Com mais de 32 bytes de proposito: sem a checagem especifica, este valor passaria
        // na de tamanho e viraria a chave de assinatura de producao.
        String placeholderLongo = "${JWT_SECRET_DE_UM_AMBIENTE_QUE_NINGUEM_CONFIGUROU}";

        assertThatThrownBy(() -> comSegredo(placeholderLongo))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nao foi resolvida");
    }

    @Test
    @DisplayName("segredo abaixo de 32 bytes nao sobe")
    void segredoCurtoEhRecusado() {
        // HS256 exige 256 bits. Abaixo disso a biblioteca recusaria no primeiro login —
        // melhor no boot, com uma mensagem que diz o que fazer.
        assertThatThrownBy(() -> comSegredo("a".repeat(31)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("31 bytes");
    }

    @Test
    @DisplayName("32 bytes exatos e o minimo aceito")
    void segredoDe32BytesEhAceito() {
        assertThatCode(() -> comSegredo("a".repeat(32))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sem emissor nao sobe")
    void emissorVazioEhRecusado() {
        assertThatThrownBy(() -> new AuthProperties(
                        SEGREDO_VALIDO, " ", TRINTA_MINUTOS, SETE_DIAS, 3, QUINZE_MINUTOS))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("validade do access token precisa ser positiva")
    void validadeDeAccessInvalidaEhRecusada() {
        assertThatThrownBy(() -> new AuthProperties(
                        SEGREDO_VALIDO, "ts", Duration.ZERO, SETE_DIAS, 3, QUINZE_MINUTOS))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AuthProperties(
                        SEGREDO_VALIDO, "ts", null, SETE_DIAS, 3, QUINZE_MINUTOS))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("validade do refresh token precisa ser positiva")
    void validadeDeRefreshInvalidaEhRecusada() {
        assertThatThrownBy(() -> new AuthProperties(
                        SEGREDO_VALIDO, "ts", TRINTA_MINUTOS, Duration.ofDays(-1), 3, QUINZE_MINUTOS))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("duracao do bloqueio precisa ser positiva")
    void duracaoDeBloqueioInvalidaEhRecusada() {
        assertThatThrownBy(() -> new AuthProperties(
                        SEGREDO_VALIDO, "ts", TRINTA_MINUTOS, SETE_DIAS, 3, Duration.ZERO))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("limite de uma tentativa e o menor aceito")
    void limiteDeUmaTentativaEhAceito() {
        // A borda exata. Sem ela, um `< 1` virado `<= 1` recusaria uma configuracao valida
        // e nenhum teste perceberia — foi o PITest que apontou.
        assertThatCode(() -> new AuthProperties(
                        SEGREDO_VALIDO, "ts", TRINTA_MINUTOS, SETE_DIAS, 1, QUINZE_MINUTOS))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("limite de tentativas zero bloquearia todo mundo no primeiro acesso")
    void limiteZeroEhRecusado() {
        assertThatThrownBy(() -> new AuthProperties(
                        SEGREDO_VALIDO, "ts", TRINTA_MINUTOS, SETE_DIAS, 0, QUINZE_MINUTOS))
                .isInstanceOf(IllegalStateException.class);
    }

    private static AuthProperties comSegredo(String segredo) {
        return new AuthProperties(segredo, "ticket-system", TRINTA_MINUTOS, SETE_DIAS, 3, QUINZE_MINUTOS);
    }
}
