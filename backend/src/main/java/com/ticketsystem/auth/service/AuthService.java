package com.ticketsystem.auth.service;

import com.ticketsystem.user.UserAccount;
import com.ticketsystem.user.UserFacade;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Login, renovacao e logout.
 *
 * <p><strong>Esta classe nao tem {@code @Transactional}, e isso e deliberado.</strong> Cada
 * passo roda na transacao do service que o executa, e o login inteiro nao e uma unidade
 * atomica. Se fosse, dois mecanismos de seguranca se desfariam sozinhos:
 * <ul>
 *   <li>a falha contada por {@link LoginLockoutService#registerFailure} seria desfeita
 *       pelo rollback da {@link InvalidCredentialsException} lancada logo depois — o
 *       bloqueio nunca dispararia;</li>
 *   <li>a revogacao de familia feita por {@link RefreshTokenService#rotate} num replay
 *       seria desfeita pela {@link InvalidRefreshTokenException} — o token do ladrao
 *       continuaria vivo.</li>
 * </ul>
 * Os dois services tem sua propria protecao ({@code REQUIRES_NEW} e {@code noRollbackFor}),
 * mas uma transacao em volta daqui anularia a segunda. Nao acrescente uma.
 */
@Service
public class AuthService {

    private final UserFacade usuarios;
    private final LoginLockoutService bloqueio;
    private final JwtService jwt;
    private final RefreshTokenService refresh;
    private final AuthProperties propriedades;

    AuthService(
            UserFacade usuarios,
            LoginLockoutService bloqueio,
            JwtService jwt,
            RefreshTokenService refresh,
            AuthProperties propriedades) {
        this.usuarios = usuarios;
        this.bloqueio = bloqueio;
        this.jwt = jwt;
        this.refresh = refresh;
        this.propriedades = propriedades;
    }

    /**
     * Confere a credencial e abre uma sessao.
     *
     * <p>A ordem e o que importa, e cada inversao e um buraco:
     * <ol>
     *   <li><b>Bloqueio antes da senha.</b> Senao a senha certa destravaria uma conta
     *       bloqueada, e quem forca a senha continuaria testando palpites durante o
     *       bloqueio.</li>
     *   <li><b>Falha contada antes de responder</b>, e so para conta que existe: nao ha o
     *       que proteger num e-mail sem conta, e contar para ele criaria linhas a partir de
     *       trafego anonimo.</li>
     *   <li><b>Mesma resposta</b> para senha errada e e-mail inexistente.</li>
     * </ol>
     */
    public Tokens login(String email, String rawPassword) {
        Optional<UserAccount> conta = usuarios.findByEmail(email);

        if (conta.isPresent() && bloqueio.isLocked(conta.get().id())) {
            throw new AccountLockedException();
        }

        Optional<UserAccount> autenticada = usuarios.authenticate(email, rawPassword);
        if (autenticada.isEmpty()) {
            conta.ifPresent(c -> bloqueio.registerFailure(c.id()));
            throw new InvalidCredentialsException();
        }

        UserAccount usuario = autenticada.get();
        bloqueio.registerSuccess(usuario.id());
        return new Tokens(
                jwt.issueAccessToken(usuario.id(), usuario.role()),
                refresh.issue(usuario.id()).value(),
                propriedades.accessTokenTtl());
    }

    /**
     * Troca o refresh token por um par novo.
     *
     * <p>O papel e lido de novo a cada renovacao, e nao herdado do token anterior: e isso
     * que faz uma promocao ou um rebaixamento valer sem exigir novo login.
     */
    public Tokens refresh(String rawRefreshToken) {
        IssuedRefreshToken novo = refresh.rotate(rawRefreshToken);
        UserAccount usuario = usuarios.findById(novo.userId());
        return new Tokens(
                jwt.issueAccessToken(usuario.id(), usuario.role()),
                novo.value(),
                propriedades.accessTokenTtl());
    }

    public void logout(String rawRefreshToken) {
        refresh.revoke(rawRefreshToken);
    }
}
