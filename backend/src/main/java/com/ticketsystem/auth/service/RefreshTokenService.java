package com.ticketsystem.auth.service;

import com.ticketsystem.auth.domain.RefreshToken;
import com.ticketsystem.auth.domain.RefreshTokenRepository;
import com.ticketsystem.auth.domain.RevocationReason;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emite, renova e revoga refresh tokens.
 *
 * <p>O token e uma string opaca de 256 bits, e nao um JWT: um JWT se valida sozinho, que
 * e o oposto de revogavel, e ele estaria em banco de qualquer forma para poder ser
 * revogado.
 */
@Service
public class RefreshTokenService {

    /** 256 bits: nao ha o que forcar bruta, e por isso SHA-256 basta no lugar de BCrypt. */
    private static final int BYTES_DE_ENTROPIA = 32;

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final RefreshTokenRepository repositorio;
    private final AuthProperties propriedades;
    private final Clock relogio;

    RefreshTokenService(RefreshTokenRepository repositorio, AuthProperties propriedades, Clock relogio) {
        this.repositorio = repositorio;
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    /** Abre uma sessao nova, numa familia nova. */
    @Transactional
    public IssuedRefreshToken issue(Long userId) {
        return emitir(userId, UUID.randomUUID(), relogio.instant());
    }

    /**
     * Troca o token atual por um novo, na mesma familia.
     *
     * <p><strong>{@code noRollbackFor} e o que faz a deteccao de reuso funcionar.</strong>
     * No caso de replay, a familia e revogada e em seguida a excecao sobe. Sem esta
     * clausula, a propria excecao desfaria a revogacao no rollback — o teste de "replay da
     * 401" passaria mesmo assim, por causa do {@code used_at}, e o token que o ladrao ja
     * trocou continuaria vivo.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public IssuedRefreshToken rotate(String rawToken) {
        RefreshToken atual = repositorio.findForRotation(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        Instant agora = relogio.instant();

        if (atual.wasConsumed()) {
            // Um token consumido so reaparece se alguem guardou uma copia. A familia
            // inteira cai, para ladrao e vitima perderem o acesso juntos em vez de so o
            // mais lento dos dois.
            repositorio.revokeFamily(atual.getFamilyId(), agora, RevocationReason.REUSE_DETECTED);
            throw new InvalidRefreshTokenException();
        }
        if (atual.isExpiredAt(agora)) {
            // Fim normal de sessao, nao roubo: nada a revogar.
            throw new InvalidRefreshTokenException();
        }

        atual.markUsed(agora);
        return emitir(atual.getUserId(), atual.getFamilyId(), agora);
    }

    /**
     * Encerra a sessao do token apresentado.
     *
     * <p>Silencioso quando o token nao existe: um erro aqui diria a quem chama se o token
     * era valido. Logout e idempotente.
     */
    @Transactional
    public void revoke(String rawToken) {
        repositorio.findForRotation(hash(rawToken)).ifPresent(token ->
                repositorio.revokeFamily(token.getFamilyId(), relogio.instant(), RevocationReason.LOGOUT));
    }

    private IssuedRefreshToken emitir(Long userId, UUID familia, Instant agora) {
        String valor = gerarValor();
        Instant validade = agora.plus(propriedades.refreshTokenTtl());
        repositorio.save(RefreshToken.issue(userId, hash(valor), familia, validade));
        return new IssuedRefreshToken(userId, valor, validade);
    }

    private static String gerarValor() {
        byte[] bytes = new byte[BYTES_DE_ENTROPIA];
        ALEATORIO.nextBytes(bytes);
        // base64url sem padding: vai em corpo JSON e, no frontend, em cookie — nenhum
        // caractere que precise de escape.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 em hexadecimal minusculo — exatamente o formato que o CHECK da V4 exige.
     *
     * <p>Deterministico e sem sal, ao contrario do BCrypt da senha: e o que permite achar o
     * token com {@code WHERE token_hash = ?} em vez de varrer a tabela.
     */
    static String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossivel) {
            // Toda JVM e obrigada pela especificacao a ter SHA-256.
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM", impossivel);
        }
    }
}
