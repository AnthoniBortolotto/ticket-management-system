package com.ticketsystem;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketsystem.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * O registro de publicacao de eventos e o que torna confiavel a comunicacao entre
 * modulos: {@code ticket} publica, {@code sla} e {@code audit} escutam, e se um listener
 * falhar o evento fica pendente e e reprocessado no proximo start — sem message broker.
 *
 * <p>Este teste existe porque a alternativa e descobrir que o mecanismo nao funciona
 * quando o primeiro SLA nao for recalculado, sem erro nenhum no log. Ele exercita o
 * caminho inteiro: grava na transacao, entrega ao listener e arquiva ao concluir.
 *
 * <p>Fica no pacote raiz, junto de {@link ModularityTest}, porque testa infraestrutura
 * que atravessa todos os modulos e nao pertence a nenhum.
 */
@IntegrationTest
@Import(EventPublicationIT.ListenerDeTeste.class)
class EventPublicationIT {

    private static final Duration LIMITE = Duration.ofSeconds(15);

    @Autowired
    ApplicationEventPublisher publicador;

    @Autowired
    ListenerDeTeste listener;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transacao;

    @Autowired
    Environment ambiente;

    @Test
    void pendenteEReentregueNoProximoStart() {
        // O default do Modulith e false. Com ele, um listener que falhasse deixaria o evento
        // pendente para sempre, sem erro nenhum no start seguinte. Nao da para reiniciar o
        // contexto dentro do teste; o que se prova aqui e que a configuracao foi lida.
        assertThat(ambiente.getProperty("spring.modulith.events.republish-outstanding-events-on-restart", Boolean.class))
            .isTrue();
    }

    @Test
    void oEventoEEntregueAoListenerEDepoisArquivado() throws Exception {
        limparRegistro();

        // Publicar fora de transacao nao gravaria nada: o registro entra na mesma
        // transacao que salvaria a entidade, e e dai que vem a garantia.
        transacao.executeWithoutResult(status ->
            publicador.publishEvent(new EventoDeTeste("ping-" + Instant.now())));

        assertThat(listener.aguardarEntrega(LIMITE))
            .as("o listener precisa receber o evento depois do commit")
            .isTrue();

        aguardarAte(() -> contarArquivados() == 1, "o evento concluido ser arquivado");

        assertThat(contarPendentes())
            .as("no modo archive a tabela ativa guarda so o que esta pendente")
            .isZero();
    }

    private void limparRegistro() {
        jdbc.execute("DELETE FROM event_publication");
        jdbc.execute("DELETE FROM event_publication_archive");
    }

    // Uma consulta literal por tabela em vez de montar o SQL com o nome recebido:
    // nome de tabela nao aceita bind parameter, entao concatenar viraria o padrao que
    // produz SQL injection quando alguem, um dia, passar um nome que veio de fora.
    private int contarPendentes() {
        return total("SELECT count(*) FROM event_publication");
    }

    private int contarArquivados() {
        return total("SELECT count(*) FROM event_publication_archive");
    }

    private int total(String sql) {
        Integer contagem = jdbc.queryForObject(sql, Integer.class);
        return contagem == null ? 0 : contagem;
    }

    /** A entrega e assincrona; sem espera o teste vira corrida e fica intermitente. */
    private static void aguardarAte(BooleanSupplier condicao, String oQue) throws InterruptedException {
        Instant limite = Instant.now().plus(LIMITE);
        while (Instant.now().isBefore(limite)) {
            if (condicao.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Tempo esgotado esperando " + oQue);
    }

    record EventoDeTeste(String valor) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ListenerDeTeste {

        private final CountDownLatch recebeu = new CountDownLatch(1);

        /**
         * A mesma anotacao que {@code sla} e {@code audit} vao usar: assincrona, em
         * transacao propria, disparada so depois do commit de quem publicou.
         */
        @ApplicationModuleListener
        void aoReceber(EventoDeTeste evento) {
            recebeu.countDown();
        }

        /**
         * Acessado por metodo, e nao pelo campo: como o listener e assincrono, o bean
         * injetado no teste e um proxy AOP, e o campo do proxy nunca e inicializado.
         * Chamada de metodo o proxy delega ao alvo; leitura de campo, nao.
         */
        boolean aguardarEntrega(Duration limite) throws InterruptedException {
            return recebeu.await(limite.toSeconds(), TimeUnit.SECONDS);
        }
    }
}
