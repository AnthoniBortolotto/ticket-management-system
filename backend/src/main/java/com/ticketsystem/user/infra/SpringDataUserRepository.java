package com.ticketsystem.user.infra;

import com.ticketsystem.user.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * O trabalho pesado, em Spring Data. E detalhe de implementacao: o service nao a enxerga.
 *
 * <p>As consultas por e-mail usam {@code IgnoreCase} porque o valor chega do formulario de
 * login sem passar pela normalizacao da entidade. Contra Postgres isso vira
 * {@code upper(email) = upper(?)}, que nao usa o indice do UNIQUE — aceitavel enquanto o
 * login e o unico caminho que consulta assim; se virar gargalo, a saida e normalizar no
 * service e voltar a comparar por igualdade.
 */
interface SpringDataUserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
