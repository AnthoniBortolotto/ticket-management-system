package com.ticketsystem.auth.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * O que o dominio precisa guardar sobre tentativas de login.
 *
 * <p>Os metodos de escrita sao atomicos por contrato — nao da para expressar isso na
 * assinatura, entao fica escrito aqui. Quem implementar outro adaptador precisa garantir
 * que duas falhas simultaneas da mesma conta resultem em duas contagens, e nao numa.
 */
public interface LoginLockoutRepository {

    Optional<LoginLockout> findByUserId(Long userId);

    /**
     * Conta mais uma falha e devolve o total resultante.
     *
     * <p>A contagem recomeca do um em dois casos, e os dois existem para o contador
     * significar "falhas seguidas e recentes", e nao "falhas desde sempre":
     * <ul>
     *   <li>a conta tinha um bloqueio que ja venceu em {@code failedAt} — quem esperou o
     *       bloqueio passar volta a ter todas as tentativas, em vez de ser rebloqueado na
     *       primeira senha errada;</li>
     *   <li>a falha anterior e mais antiga que {@code forgetFailuresBefore} — sem isso,
     *       duas senhas erradas num mes e uma terceira semanas depois bloqueariam a conta.</li>
     * </ul>
     */
    int registerFailure(Long userId, Instant failedAt, Instant forgetFailuresBefore);

    void lockUntil(Long userId, Instant until);

    /** Apaga o historico de falhas. Chamado no login bem-sucedido. */
    void clear(Long userId);
}
