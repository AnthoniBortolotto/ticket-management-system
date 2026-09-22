package com.ticketsystem.user;

import com.ticketsystem.user.service.UserService;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * A unica porta do modulo {@code user}.
 *
 * <p>{@code UserService}, {@code User} e {@code UserRepository} sao invisiveis de fora: o
 * Modulith quebra o build se outro modulo os importar. Quem precisa de usuario passa por
 * aqui.
 *
 * <p>Note {@link #authenticate}: ela pergunta "esta senha confere?" em vez de devolver o
 * hash para quem chama comparar. E o que mantem a credencial dentro do modulo — uma
 * consulta do tipo {@code findCredentialsByEmail} tornaria esta fachada um vazamento com
 * fachada em volta.
 */
@Component
public class UserFacade {

    private final UserService service;

    UserFacade(UserService service) {
        this.service = service;
    }

    /**
     * Confere credencial e devolve a conta, ou vazio.
     *
     * <p>Vazio significa "senha errada <em>ou</em> conta inexistente", sem distinguir: quem
     * chama nao consegue transformar o login num verificador de contas nem por engano.
     */
    public Optional<UserAccount> authenticate(String email, String rawPassword) {
        return service.authenticate(email, rawPassword);
    }

    /** Lanca se nao existir — quem so quer saber se existe nao deveria perguntar por id. */
    public UserAccount findById(Long id) {
        return service.findById(id);
    }
}
