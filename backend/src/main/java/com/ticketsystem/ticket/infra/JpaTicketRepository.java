package com.ticketsystem.ticket.infra;

import com.ticketsystem.ticket.domain.Ticket;
import com.ticketsystem.ticket.domain.TicketPage;
import com.ticketsystem.ticket.domain.TicketPriority;
import com.ticketsystem.ticket.domain.TicketRepository;
import com.ticketsystem.ticket.domain.VisibilityScope;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/**
 * O adaptador JPA da porta de tickets. Roda o {@code TicketRepositoryContractTest} — e um
 * adaptador que o substitua precisa passar pela mesma suite, sem ela ser reescrita.
 *
 * <p>A recusa de versao desatualizada que o contrato exige vem do {@code @Version} da
 * entidade: o Spring Data traduz o {@code OptimisticLockException} do Hibernate para
 * {@code OptimisticLockingFailureException}, que e o tipo que o contrato nomeia.
 *
 * <p>A listagem monta a consulta a mao, com Criteria, em vez de passar por
 * {@code JpaSpecificationExecutor}: a ordem por prioridade nao e uma coluna — {@code URGENT}
 * vem antes de {@code LOW}, e a coluna guarda o nome —, e o {@code Sort} do Spring Data so
 * ordena por propriedade. O filtro continua sendo o {@link TicketSpecifications}, aplicado
 * igual na pagina e na contagem.
 */
@Repository
class JpaTicketRepository implements TicketRepository {

    private final SpringDataTicketRepository springData;
    private final EntityManager em;

    JpaTicketRepository(SpringDataTicketRepository springData, EntityManager em) {
        this.springData = springData;
        this.em = em;
    }

    @Override
    public Optional<Ticket> findById(Long id) {
        return springData.findById(id);
    }

    @Override
    public Ticket save(Ticket ticket) {
        return springData.save(ticket);
    }

    @Override
    public TicketPage findVisible(VisibilityScope scope, int page, int size) {
        Specification<Ticket> visiveis = TicketSpecifications.visibleTo(scope);
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<Ticket> consulta = cb.createQuery(Ticket.class);
        Root<Ticket> ticket = consulta.from(Ticket.class);
        consulta.select(ticket)
                .where(visiveis.toPredicate(ticket, consulta, cb))
                .orderBy(cb.asc(urgencia(cb, ticket)), cb.asc(ticket.get("createdAt")), cb.asc(ticket.get("id")));
        List<Ticket> pagina = em.createQuery(consulta)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();

        CriteriaQuery<Long> contagem = cb.createQuery(Long.class);
        Root<Ticket> contado = contagem.from(Ticket.class);
        contagem.select(cb.count(contado)).where(visiveis.toPredicate(contado, contagem, cb));

        return new TicketPage(pagina, page, size, em.createQuery(contagem).getSingleResult());
    }

    @Override
    public List<Ticket> findInTeamWithCurrentAssignee(Long teamId, Long userId) {
        return springData.findByAssignedTeamIdAndCurrentAssigneeId(teamId, userId);
    }

    /**
     * 0 para {@code URGENT}, 3 para {@code LOW}. A ordem sai do enum, que e a fonte da verdade:
     * uma prioridade nova no fim dele passa a ser a mais urgente sem ninguem mexer aqui.
     */
    private static Expression<Integer> urgencia(CriteriaBuilder cb, Root<Ticket> ticket) {
        TicketPriority[] prioridades = TicketPriority.values();
        CriteriaBuilder.Case<Integer> caso = cb.selectCase();
        for (TicketPriority prioridade : prioridades) {
            caso = caso.when(cb.equal(ticket.get("priority"), prioridade), prioridades.length - 1 - prioridade.ordinal());
        }
        return caso.otherwise(prioridades.length);
    }
}
