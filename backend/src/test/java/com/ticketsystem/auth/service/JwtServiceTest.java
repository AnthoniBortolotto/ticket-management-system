package com.ticketsystem.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.ticketsystem.user.UserRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * O que o token de acesso carrega, e o que ele deliberadamente nao carrega.
 *
 * <p>Unitario: monta encoder e decoder com chave fixa e relogio parado, sem contexto
 * Spring. Relogio parado, e nao {@code Instant.now()}, para o teste de expiracao ser
 * exato em vez de dormir.
 */
class JwtServiceTest {

    private static final String SEGREDO = "um-segredo-de-teste-com-mais-de-32-bytes-de-tamanho";
    private static final Instant AGORA = Instant.parse("2026-09-21T12:00:00Z");

    private final Clock relogio = Clock.fixed(AGORA, ZoneOffset.UTC);
    private final AuthProperties propriedades =
            new AuthProperties(
                    SEGREDO,
                    "ticket-system",
                    Duration.ofMinutes(30),
                    Duration.ofDays(7),
                    3,
                    Duration.ofMinutes(15));

    private final JwtService service =
            new JwtService(encoder(SEGREDO), propriedades, relogio);

    @Test
    @DisplayName("o token identifica o usuario e o papel dele")
    void tokenCarregaUsuarioEPapel() {
        var jwt = decoder(SEGREDO).decode(service.issueAccessToken(42L, UserRole.AGENT));

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("AGENT");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("ticket-system");
    }

    @Test
    @DisplayName("o token NAO carrega as equipes do usuario")
    void tokenNaoCarregaEquipes() {
        var jwt = decoder(SEGREDO).decode(service.issueAccessToken(42L, UserRole.AGENT));

        // Este teste documenta uma decisao de desenho, nao um detalhe. O que esta dentro
        // de um JWT so muda quando ele expira: com as equipes ali, tirar alguem de uma
        // equipe continuaria dando acesso aos tickets dela ate o token vencer — janela de
        // vazamento na parte mais cara de errar do sistema. A Fase 5 resolve equipe por
        // requisicao, via TeamFacade.
        assertThat(jwt.getClaims()).doesNotContainKeys("teams", "equipes", "team_ids");
    }

    @Test
    @DisplayName("a validade sai do relogio e da configuracao, nao de um valor fixo no codigo")
    void validadeVemDaConfiguracao() {
        var jwt = decoder(SEGREDO).decode(service.issueAccessToken(42L, UserRole.ADMIN));

        assertThat(jwt.getIssuedAt()).isEqualTo(AGORA);
        assertThat(jwt.getExpiresAt()).isEqualTo(AGORA.plus(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("cada token tem identificador proprio")
    void cadaTokenTemIdentificadorProprio() {
        var um = decoder(SEGREDO).decode(service.issueAccessToken(42L, UserRole.AGENT));
        var outro = decoder(SEGREDO).decode(service.issueAccessToken(42L, UserRole.AGENT));

        // Sem jti, dois tokens emitidos no mesmo segundo para o mesmo usuario sao
        // byte a byte identicos — e nao ha como distingui-los num log de auditoria.
        assertThat(um.getId()).isNotBlank().isNotEqualTo(outro.getId());
    }

    @Test
    @DisplayName("token assinado com outra chave nao e aceito")
    void tokenDeOutraChaveNaoEhAceito() {
        var deOutroEmissor =
                new JwtService(
                                encoder("outro-segredo-completamente-diferente-e-longo-o-bastante"),
                                propriedades,
                                relogio)
                        .issueAccessToken(42L, UserRole.ADMIN);

        // Se o decoder aceitasse, qualquer um forjaria um token de ADMIN.
        assertThatThrownBy(() -> decoder(SEGREDO).decode(deOutroEmissor))
                .isInstanceOf(JwtException.class);
    }

    private static NimbusJwtEncoder encoder(String segredo) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(chave(segredo)));
    }

    /**
     * Decoder que confere a assinatura mas nao o relogio.
     *
     * <p>O {@link JwtService} aqui emite com um {@code Clock} parado em {@link #AGORA},
     * entao contra o relogio real da maquina todo token nasceria expirado. Estes testes
     * afirmam <em>o conteudo</em> das claims; que um token expirado seja recusado e
     * assunto do {@code SecurityIT}, contra a cadeia de filtros de verdade.
     *
     * <p>A verificacao de assinatura continua ligada — e o que faz
     * {@link #tokenDeOutraChaveNaoEhAceito()} ter valor.
     */
    private static JwtDecoder decoder(String segredo) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(chave(segredo))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(token -> OAuth2TokenValidatorResult.success());
        return decoder;
    }

    private static SecretKeySpec chave(String segredo) {
        return new SecretKeySpec(segredo.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
    }
}
