package com.ticketsystem.auth;

import com.ticketsystem.auth.infra.JwtCurrentUserResolver;
import org.springframework.stereotype.Component;

/**
 * A porta publica do modulo {@code auth} para os outros modulos.
 *
 * <p>Responde uma pergunta so: quem esta pedindo. O controller chama isto e entrega o
 * {@link CurrentUser} ao service, em vez de o service ler o contexto de seguranca por
 * conta propria — assim a regra de permissao recebe o ator como parametro e e testavel
 * sem Spring Security nenhum.
 */
@Component
public class AuthFacade {

    private final JwtCurrentUserResolver resolver;

    AuthFacade(JwtCurrentUserResolver resolver) {
        this.resolver = resolver;
    }

    /** Lanca 401 se nao houver usuario autenticado — nunca devolve nulo. */
    public CurrentUser currentUser() {
        return resolver.resolve();
    }
}
