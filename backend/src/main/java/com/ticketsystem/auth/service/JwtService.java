package com.ticketsystem.auth.service;

import com.ticketsystem.user.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Emite o token de acesso.
 *
 * <p>Fino de proposito: monta as claims e delega a assinatura. Toda a fiacao — chave,
 * algoritmo, encoder — vive em {@code auth.infra.JwtConfig}, o que deixa esta classe
 * testavel sem contexto Spring.
 *
 * <p>O {@link Clock} e injetado, e nao {@code Instant.now()}: sem isso, testar expiracao
 * exigiria dormir, o que deixa a suite lenta e intermitente.
 */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final AuthProperties propriedades;
    private final Clock relogio;

    public JwtService(JwtEncoder encoder, AuthProperties propriedades, Clock relogio) {
        this.encoder = encoder;
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    /**
     * Um token que diz quem e a pessoa e qual o papel global dela. Nada alem disso.
     *
     * <p><strong>As equipes nao entram.</strong> Claim dentro de um JWT so muda quando o
     * token expira: com as equipes aqui, tirar alguem de uma equipe continuaria dando
     * acesso aos tickets dela ate o vencimento. A visibilidade e a parte mais cara de
     * errar do sistema, entao ela e resolvida por requisicao, contra o banco, e nao
     * congelada na credencial.
     */
    public String issueAccessToken(Long userId, UserRole role) {
        Instant agora = relogio.instant();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(propriedades.issuer())
                .subject(String.valueOf(userId))
                .claim("role", role.name())
                .issuedAt(agora)
                .expiresAt(agora.plus(propriedades.accessTokenTtl()))
                // Sem isto, dois tokens emitidos no mesmo segundo para o mesmo usuario
                // sao identicos byte a byte e nao ha como distingui-los numa auditoria.
                .id(UUID.randomUUID().toString())
                .build();

        // O cabecalho vai explicito: sem ele o NimbusJwtEncoder assume RS256 e falha com
        // "Failed to select a JWK signing key", porque a chave configurada e simetrica.
        JwsHeader cabecalho = JwsHeader.with(MacAlgorithm.HS256).build();

        return encoder.encode(JwtEncoderParameters.from(cabecalho, claims)).getTokenValue();
    }
}
