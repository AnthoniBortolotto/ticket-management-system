package com.ticketsystem.auth.infra;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.ticketsystem.auth.service.AuthProperties;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * A fiacao do JWT: chave, quem assina, quem valida e como o papel vira permissao.
 *
 * <p>Fica em {@code infra} de proposito. O {@code JwtService} so monta claims e delega, e
 * por isso e testavel sem contexto Spring; toda a dependencia de biblioteca esta aqui.
 *
 * <p>Nao ha propriedade do Boot para chave HMAC — {@code spring.security.oauth2.resourceserver.jwt.*}
 * cobre apenas {@code issuer-uri}, {@code jwk-set-uri} e {@code public-key-location}, que
 * sao os caminhos assimetricos. Por isso os beans sao montados a mao.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
class JwtConfig {

    private static final Log log = LogFactory.getLog(JwtConfig.class);

    /** O claim onde o papel global viaja. Precisa casar com o que o JwtService emite. */
    static final String CLAIM_DE_PAPEL = "role";

    private final AuthProperties propriedades;

    JwtConfig(AuthProperties propriedades) {
        this.propriedades = propriedades;
    }

    /**
     * A chave simetrica.
     *
     * <p>UTF-8 cru, e <strong>nao</strong> base64: o valor publicado em
     * {@code backend/.env.example} tem hifens e nao e base64 valido. Decodificar como
     * base64 faria o projeto parar de subir para quem seguiu o README.
     */
    private SecretKey chave() {
        return new SecretKeySpec(
                propriedades.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder() {
        // Confirmacao de que a tecnologia inicializou, como manda o CLAUDE.md. O tamanho
        // da chave vai no log; o segredo, jamais.
        log.info("Assinatura de token: HS256, chave de %d bytes, access token valido por %s"
                .formatted(
                        propriedades.secret().getBytes(StandardCharsets.UTF_8).length,
                        propriedades.accessTokenTtl()));

        return new NimbusJwtEncoder(new ImmutableSecret<>(chave()));
    }

    @Bean
    JwtDecoder jwtDecoder() {
        // macAlgorithm explicito: sem ele o decoder aceitaria qualquer algoritmo que a
        // chave suporte, e travar o algoritmo e parte de nao aceitar token forjado.
        return NimbusJwtDecoder.withSecretKey(chave()).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /**
     * Converte o claim {@code role} em authority do Spring Security.
     *
     * <p>E o pedaco que costuma quebrar em silencio: com o nome do claim errado ou sem o
     * prefixo {@code ROLE_}, todo mundo autentica com zero permissoes e recebe 403 em
     * tudo — o que parece bug de permissao e e bug de conversao. Por isso os testes de
     * autorizacao usam token assinado de verdade, e nunca {@code @WithMockUser}, que
     * pularia exatamente esta linha.
     */
    @Bean
    Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        var permissoes = new JwtGrantedAuthoritiesConverter();
        permissoes.setAuthoritiesClaimName(CLAIM_DE_PAPEL);
        // hasRole("ADMIN") procura a authority "ROLE_ADMIN".
        permissoes.setAuthorityPrefix("ROLE_");

        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(permissoes);
        return converter;
    }
}
