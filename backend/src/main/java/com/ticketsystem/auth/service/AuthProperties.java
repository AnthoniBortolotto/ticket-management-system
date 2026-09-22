package com.ticketsystem.auth.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tudo que o login precisa saber e que muda por ambiente.
 *
 * <p>Repare no que <strong>nao</strong> esta aqui: nenhum default para o segredo. Um
 * default no codigo valeria para todo ambiente, inclusive os que ninguem configurou — e
 * assinar token com uma chave publicada no repositorio e o mesmo erro que o
 * {@code ADMIN_PASSWORD_HASH} quase produziu na fase anterior.
 *
 * <p>A validacao no construtor existe por causa de uma armadilha ja confirmada neste
 * projeto e registrada no CLAUDE.md: <strong>variavel de ambiente ausente nao gera erro no
 * Spring</strong>. O binder deixa o texto {@code ${JWT_SECRET}} como valor, e sem esta
 * checagem a aplicacao subiria assinando tokens com essa string literal como chave. Por
 * isso ela e recusada por nome.
 *
 * <p>O limiar de bloqueio fica aqui, e nao em banco. A distincao em relacao ao SLA, que o
 * CLAUDE.md manda guardar em banco: prazo de SLA e dado de negocio que um admin edita em
 * runtime; limiar de bloqueio e parametro operacional de seguranca, que muda com deploy.
 */
@ConfigurationProperties("ticketsystem.auth")
public record AuthProperties(
        String secret,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        int maxFailedAttempts,
        Duration lockDuration) {

    /** HS256 exige chave de pelo menos 256 bits; abaixo disso a biblioteca recusa. */
    private static final int MINIMO_DE_BYTES = 32;

    public AuthProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "ticketsystem.auth.secret nao esta definido. Copie backend/.env.example "
                            + "para backend/.env ou defina JWT_SECRET no ambiente.");
        }
        if (secret.contains("${")) {
            throw new IllegalStateException(
                    "ticketsystem.auth.secret veio como o texto literal '" + secret
                            + "': a variavel de ambiente nao foi resolvida. O Spring nao "
                            + "reclama disso sozinho — defina JWT_SECRET.");
        }
        int bytes = secret.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < MINIMO_DE_BYTES) {
            throw new IllegalStateException(
                    "ticketsystem.auth.secret tem " + bytes + " bytes; HS256 exige ao menos "
                            + MINIMO_DE_BYTES + ".");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("ticketsystem.auth.issuer e obrigatorio.");
        }
        exigirPositivo(accessTokenTtl, "access-token-ttl");
        exigirPositivo(refreshTokenTtl, "refresh-token-ttl");
        exigirPositivo(lockDuration, "lock-duration");
        if (maxFailedAttempts < 1) {
            throw new IllegalStateException(
                    "ticketsystem.auth.max-failed-attempts precisa ser ao menos 1; "
                            + "zero bloquearia todo mundo no primeiro acesso.");
        }
    }

    private static void exigirPositivo(Duration valor, String nome) {
        if (valor == null || valor.isZero() || valor.isNegative()) {
            throw new IllegalStateException(
                    "ticketsystem.auth." + nome + " precisa ser uma duracao positiva.");
        }
    }
}
