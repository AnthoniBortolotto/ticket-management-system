package com.ticketsystem.auth.infra;

import com.ticketsystem.auth.CurrentUser;
import com.ticketsystem.auth.service.NotAuthenticatedException;
import com.ticketsystem.user.UserRole;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Transforma o {@code Authentication} que o resource server montou num {@link CurrentUser}.
 *
 * <p>Mora ao lado do {@link JwtConfig} porque e o outro lado da mesma conversao: la o
 * claim {@code role} vira a authority {@code ROLE_*}; aqui ela volta a ser
 * {@link UserRole}. O papel e lido das <em>authorities</em>, e nao do claim cru, de
 * proposito — e exatamente o que o {@code hasRole} do {@code SecurityConfig} enxerga, entao
 * a regra por URL e a regra do service nunca discordam sobre o papel de alguem.
 *
 * <p>O id e o {@code name} do token, que o {@code JwtAuthenticationConverter} preenche com
 * o {@code sub}.
 *
 * <p>Toda duvida vira 401. Nao ha papel default nem id "melhor esforco": qualquer valor
 * escolhido na falta do verdadeiro concederia algo que o token nao concedeu.
 */
@Component
public class JwtCurrentUserResolver {

    private static final Set<String> PAPEIS_CONHECIDOS = Arrays.stream(UserRole.values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    public CurrentUser resolve() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        // So o token do resource server conta. O filtro de anonimo poe um Authentication
        // no contexto das rotas publicas, e "ha alguem no contexto" nao e "sabemos quem e".
        if (!(autenticacao instanceof JwtAuthenticationToken token) || !token.isAuthenticated()) {
            throw new NotAuthenticatedException();
        }
        return new CurrentUser(idDe(token), papelDe(token));
    }

    private static Long idDe(JwtAuthenticationToken token) {
        try {
            return Long.valueOf(token.getName());
        } catch (NumberFormatException subjectNaoNumerico) {
            throw new NotAuthenticatedException();
        }
    }

    /**
     * Exatamente um papel conhecido. O login emite um so; dois ou nenhum significam token
     * que nao saiu daqui, e escolher um deles seria decidir permissao pela ordem de uma
     * lista.
     */
    private static UserRole papelDe(JwtAuthenticationToken token) {
        List<String> papeis = token.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(JwtConfig.PREFIXO_DE_PAPEL))
                .map(authority -> authority.substring(JwtConfig.PREFIXO_DE_PAPEL.length()))
                .toList();
        if (papeis.size() != 1 || !PAPEIS_CONHECIDOS.contains(papeis.getFirst())) {
            throw new NotAuthenticatedException();
        }
        return UserRole.valueOf(papeis.getFirst());
    }
}
