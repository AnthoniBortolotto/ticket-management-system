package com.ticketsystem.ticket.infra;

import com.ticketsystem.ticket.domain.Ticket;
import com.ticketsystem.ticket.domain.VisibilityScope;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * A regra de visibilidade (CLAUDE.md#visibilidade) em SQL, para a listagem filtrar no banco.
 *
 * <p><strong>E a mesma regra do {@code TicketAccessPolicy}, escrita outra vez.</strong> Aquela
 * decide sobre um ticket ja carregado; esta, sobre todos de uma vez. Se as duas divergirem,
 * alguem ve na lista o que nao abre, ou abre pela URL o que a lista esconde — e o
 * {@code TicketSpecificationsIT} compara as duas caso a caso. <strong>Mudou aqui, mude la.</strong>
 *
 * <p>Uma pessoa ve um ticket quando qualquer uma destas vale:
 * <ul>
 *   <li>e o solicitante — em qualquer modo;</li>
 *   <li>e o responsavel exclusivo;</li>
 *   <li>participa da equipe do ticket em modo equipe;</li>
 *   <li>lidera a equipe de onde o ticket saiu para o modo exclusivo.</li>
 * </ul>
 * As tres ultimas so para quem atende: o papel {@code REQUESTER} fica com a primeira, mesmo
 * que um vinculo de equipe esquecido diga outra coisa. Admin nao passa por filtro nenhum.
 *
 * <p>Os conjuntos de equipes chegam prontos no escopo, lidos do banco na hora pela
 * {@code TeamFacade}: {@code ticket} nao consulta as tabelas de {@code team}, e a fronteira
 * entre os modulos vale no SQL tambem. Conjunto vazio nao vira {@code IN ()} — a clausula
 * simplesmente nao entra.
 */
final class TicketSpecifications {

    private TicketSpecifications() {
    }

    static Specification<Ticket> visibleTo(VisibilityScope scope) {
        return (ticket, consulta, cb) -> {
            if (scope.unrestricted()) {
                return cb.conjunction();
            }
            List<Predicate> alcance = new ArrayList<>();
            alcance.add(cb.equal(ticket.get("requesterId"), scope.userId()));
            if (scope.handlesTickets()) {
                alcance.add(cb.equal(ticket.get("exclusiveAssigneeId"), scope.userId()));
                if (!scope.memberOf().isEmpty()) {
                    // assigned_team_id so e preenchido em modo equipe (CHECK da V5): esta
                    // clausula nunca alcanca um ticket que saiu da equipe.
                    alcance.add(ticket.get("assignedTeamId").in(scope.memberOf()));
                }
                if (!scope.leads().isEmpty()) {
                    alcance.add(cb.and(
                            cb.isNotNull(ticket.get("exclusiveAssigneeId")),
                            ticket.get("originTeamId").in(scope.leads())));
                }
            }
            return cb.or(alcance.toArray(Predicate[]::new));
        };
    }
}
