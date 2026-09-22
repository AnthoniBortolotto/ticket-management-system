package com.ticketsystem.user.service;

import com.ticketsystem.user.UserAccount;
import com.ticketsystem.user.UserRole;
import com.ticketsystem.user.domain.User;
import com.ticketsystem.user.domain.UserRepository;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de usuario. Unico lugar do sistema que toca no {@link PasswordEncoder}.
 *
 * <p>E aqui que a credencial e conferida, e o resultado que sai e um {@link UserAccount} —
 * nunca o hash. Essa e a razao de {@code auth} nao precisar enxergar
 * {@code users.password_hash}: ele pergunta "esta senha confere?" em vez de "qual e o
 * hash?".
 */
@Service
public class UserService {

    /**
     * Hash BCrypt valido de uma senha que ninguem conhece, usado quando o e-mail nao
     * existe.
     *
     * <p>Sem ele, o caminho "conta inexistente" responderia imediatamente e o caminho
     * "senha errada" gastaria os ~80ms do BCrypt. Essa diferenca de tempo e observavel de
     * fora e revela quais e-mails tem conta, sem que nenhuma resposta seja diferente.
     */
    private static final String HASH_DESCARTAVEL =
            "$2a$10$qKPdARdr8P.zVL8vWjLuIuuVew89Vd38WEBlkyecf5FAmxBk.54IS";

    private final UserRepository repositorio;
    private final PasswordEncoder encoder;

    UserService(UserRepository repositorio, PasswordEncoder encoder) {
        this.repositorio = repositorio;
        this.encoder = encoder;
    }

    /**
     * Confere a credencial.
     *
     * <p>Devolve vazio tanto para senha errada quanto para e-mail inexistente, e de
     * proposito: distinguir os dois casos na resposta transforma o login num verificador
     * de contas. Quem chama tambem nao consegue distinguir — nao ha o que vazar adiante.
     */
    @Transactional(readOnly = true)
    public Optional<UserAccount> authenticate(String email, String rawPassword) {
        Optional<User> encontrado = repositorio.findByEmail(email);

        String hashParaComparar =
                encontrado.map(User::getPasswordHash).orElse(HASH_DESCARTAVEL);
        boolean confere = encoder.matches(rawPassword, hashParaComparar);

        return confere ? encontrado.map(UserService::comoConta) : Optional.empty();
    }

    @Transactional
    public UserAccount create(String email, String fullName, String rawPassword, UserRole role) {
        // O repositorio ignora a caixa nesta consulta, entao a checagem nao depende de
        // quem chama ter normalizado o valor antes.
        if (repositorio.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }
        User novo = new User(email, fullName, encoder.encode(rawPassword), role);
        return comoConta(repositorio.save(novo));
    }

    /**
     * A conta de um e-mail, sem conferir senha.
     *
     * <p>Existe para o login consultar o bloqueio <em>antes</em> de verificar a senha. Nao
     * exponha isto para fora do servidor: devolver "existe"/"nao existe" a quem chama a API
     * transformaria qualquer endpoint num verificador de contas.
     */
    @Transactional(readOnly = true)
    public Optional<UserAccount> findByEmail(String email) {
        return repositorio.findByEmail(email).map(UserService::comoConta);
    }

    @Transactional(readOnly = true)
    public UserAccount findById(Long id) {
        return repositorio.findById(id).map(UserService::comoConta)
                .orElseThrow(UserNotFoundException::new);
    }

    private static UserAccount comoConta(User usuario) {
        return new UserAccount(
                usuario.getId(), usuario.getEmail(), usuario.getFullName(), usuario.getRole());
    }
}
