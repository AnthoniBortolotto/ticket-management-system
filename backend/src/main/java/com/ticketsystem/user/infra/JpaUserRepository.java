package com.ticketsystem.user.infra;

import com.ticketsystem.user.domain.User;
import com.ticketsystem.user.domain.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Traduz a interface de dominio para o Spring Data.
 *
 * <p>Esta classe e a razao de o service nunca importar {@code org.springframework.data}:
 * ela e o adaptador, e trocar o armazenamento significa escrever outra como esta, sem
 * tocar em regra de negocio.
 */
@Repository
class JpaUserRepository implements UserRepository {

    private final SpringDataUserRepository springData;

    JpaUserRepository(SpringDataUserRepository springData) {
        this.springData = springData;
    }

    @Override
    public Optional<User> findById(Long id) {
        return springData.findById(id);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return springData.findByEmailIgnoreCase(email);
    }

    @Override
    public boolean existsByEmail(String email) {
        return springData.existsByEmailIgnoreCase(email);
    }

    @Override
    public User save(User user) {
        return springData.save(user);
    }
}
