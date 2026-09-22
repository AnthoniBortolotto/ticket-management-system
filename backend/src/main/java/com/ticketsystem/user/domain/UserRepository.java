package com.ticketsystem.user.domain;

import java.util.Optional;

/**
 * O que o dominio precisa perguntar sobre usuarios.
 *
 * <p>Interface declarada aqui, implementacao em {@code user.infra}: e este o ponto de
 * troca de armazenamento. O service conhece so esta interface e nunca importa
 * {@code jakarta.persistence} nem {@code org.springframework.data} — sem isso, trocar o
 * Postgres por outra coisa passaria a mexer em regra de negocio.
 *
 * <p>Os metodos falam a linguagem do negocio, nao a do banco. Quem implementar precisa
 * respeitar um contrato que nao da para expressar na assinatura: <strong>a busca por
 * e-mail ignora a caixa</strong>. A entidade ja normaliza para minusculas ao gravar, mas
 * o valor que chega do formulario de login nao passou por ela.
 */
public interface UserRepository {

    Optional<User> findById(Long id);

    /** Ignora a caixa do que foi digitado: 'Ana@x.com' encontra a conta 'ana@x.com'. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    User save(User user);
}
