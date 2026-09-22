package com.ticketsystem.support;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.ticketsystem.user.UserRole;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Tokens de teste: validos, e cada um dos jeitos de um token ser invalido.
 *
 * <p>Usa o {@link JwtEncoder} <em>da aplicacao</em> para os tokens que deveriam valer —
 * e isso prova, de quebra, que quem assina e quem valida usam a mesma chave. Um encoder
 * paralelo montado aqui com uma chave copiada deixaria o teste verde mesmo se a
 * configuracao estivesse errada.
 *
 * <p>Nenhum teste monta token malicioso na mao: as forjas moram aqui, com nome, para ficar
 * claro o que cada uma ataca.
 */
public final class AuthTokens {

    private final JwtEncoder encoderDaAplicacao;

    public AuthTokens(JwtEncoder encoderDaAplicacao) {
        this.encoderDaAplicacao = encoderDaAplicacao;
    }

    /** Um token como o login emitiria. */
    public String valid(long userId, UserRole role) {
        Instant agora = Instant.now();
        return assinar(encoderDaAplicacao, userId, role, agora, agora.plus(Duration.ofMinutes(30)));
    }

    /**
     * Assinatura perfeita, validade vencida.
     *
     * <p>Vencido ha uma hora, e nao ha um segundo: o validador de tempo do Nimbus tolera 60
     * segundos de diferenca de relogio, entao um token "recem-vencido" passaria e o teste
     * afirmaria a coisa errada.
     */
    public String expired(long userId, UserRole role) {
        Instant agora = Instant.now();
        return assinar(encoderDaAplicacao, userId, role,
                agora.minus(Duration.ofHours(2)), agora.minus(Duration.ofHours(1)));
    }

    /** Estrutura valida, assinada com uma chave que a aplicacao nao conhece. */
    public static String signedWithOtherKey(long userId, UserRole role) {
        var chaveAlheia = new SecretKeySpec(
                "uma-chave-que-a-aplicacao-nunca-viu-e-longa-o-bastante".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        var encoderAlheio = new NimbusJwtEncoder(new ImmutableSecret<>(chaveAlheia));
        Instant agora = Instant.now();
        return assinar(encoderAlheio, userId, role, agora, agora.plus(Duration.ofMinutes(30)));
    }

    /**
     * O ataque de verdade: pega um token legitimo, troca o papel no payload e mantem a
     * assinatura original. Se o decoder so parseasse sem conferir a assinatura, isto daria
     * acesso de admin a qualquer um com uma conta de solicitante.
     */
    public static String withRoleSwapped(String tokenLegitimo, UserRole de, UserRole para) {
        String[] partes = tokenLegitimo.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
        String adulterado = payload.replace(
                "\"role\":\"" + de.name() + "\"", "\"role\":\"" + para.name() + "\"");
        if (adulterado.equals(payload)) {
            // Sem esta checagem, uma mudanca no formato do payload faria o teste enviar o
            // token original intacto — e ele passaria afirmando o oposto do que deveria.
            throw new IllegalStateException("o papel " + de + " nao foi encontrado no payload: " + payload);
        }
        return partes[0] + "." + base64Url(adulterado) + "." + partes[2];
    }

    /** {@code alg: none} — um token que se declara sem assinatura. */
    public static String unsigned(long userId, UserRole role) {
        long expira = Instant.now().plus(Duration.ofMinutes(30)).getEpochSecond();
        String cabecalho = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = base64Url(
                "{\"sub\":\"%d\",\"role\":\"%s\",\"exp\":%d}".formatted(userId, role.name(), expira));
        return cabecalho + "." + payload + ".";
    }

    private static String assinar(
            JwtEncoder encoder, long userId, UserRole role, Instant emitidoEm, Instant expiraEm) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .claim("role", role.name())
                .issuedAt(emitidoEm)
                .expiresAt(expiraEm)
                .build();
        JwsHeader cabecalho = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(cabecalho, claims)).getTokenValue();
    }

    private static String base64Url(String texto) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }
}
