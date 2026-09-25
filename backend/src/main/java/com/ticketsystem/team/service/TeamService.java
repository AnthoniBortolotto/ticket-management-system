package com.ticketsystem.team.service;

import com.ticketsystem.auth.CurrentUser;
import com.ticketsystem.team.TeamMembershipRemoved;
import com.ticketsystem.team.UserTeams;
import com.ticketsystem.team.domain.Team;
import com.ticketsystem.team.domain.TeamMembership;
import com.ticketsystem.team.domain.TeamMembershipRepository;
import com.ticketsystem.team.domain.TeamRepository;
import com.ticketsystem.team.domain.TeamRole;
import com.ticketsystem.user.UserAccount;
import com.ticketsystem.user.UserFacade;
import com.ticketsystem.user.UserRole;
import java.time.Clock;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Criacao de equipes e gestao de membros — e quem pode fazer cada coisa.
 *
 * <p><strong>A regra de permissao mora inteira aqui</strong>, e nao dividida com o
 * {@code SecurityConfig}. Por URL so da para decidir pelo papel global; "lidera esta
 * equipe?" depende do banco. Partir a regra entre os dois lugares faria cada um parecer
 * completo sozinho.
 *
 * <p>A regra, em ordem:
 * <ol>
 *   <li>Quem nao e admin nem participa da equipe recebe 404 em tudo sobre ela — a mesma
 *       resposta de uma equipe que nao existe. 403 confirmaria o id.</li>
 *   <li>Adicionar e remover membro comum: admin ou lider <em>desta</em> equipe.</li>
 *   <li>Tudo que cria ou desfaz lideranca — promover, rebaixar, remover um lider, inclusive
 *       a si mesmo — e so de admin. O lider enxerga os tickets que sairam da equipe para o
 *       modo exclusivo; conceder isso e decisao de quem administra, nao de um par.</li>
 *   <li>Criar equipe: so admin.</li>
 * </ol>
 *
 * <p>O ator chega como parametro. O controller o obtem da {@code AuthFacade}; o service
 * nao le o contexto de seguranca, e por isso as regras acima sao testaveis sem Spring.
 */
@Service
public class TeamService {

    private final TeamRepository equipes;
    private final TeamMembershipRepository vinculos;
    private final UserFacade usuarios;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    TeamService(TeamRepository equipes, TeamMembershipRepository vinculos, UserFacade usuarios,
            ApplicationEventPublisher eventos, Clock relogio) {
        this.equipes = equipes;
        this.vinculos = vinculos;
        this.usuarios = usuarios;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    @Transactional
    public Team create(CurrentUser ator, String name, String description) {
        exigirAdmin(ator);
        // A entidade valida e apara o nome; a checagem usa o nome como ela o guarda, para
        // " Suporte" nao passar aqui e estourar no indice.
        Team nova = new Team(name, description);
        if (equipes.existsByName(nova.getName())) {
            throw new TeamNameAlreadyUsedException();
        }
        return equipes.save(nova);
    }

    @Transactional(readOnly = true)
    public TeamDetails findById(CurrentUser ator, Long teamId) {
        Team equipe = acessar(ator, teamId).equipe();
        return new TeamDetails(equipe, vinculos.findByTeam(teamId));
    }

    @Transactional
    public TeamMembership addMember(CurrentUser ator, Long teamId, Long userId) {
        exigirGestaoDeMembros(acessar(ator, teamId));

        UserAccount pessoa = usuarios.findById(userId);
        if (pessoa.role() == UserRole.REQUESTER) {
            throw new IneligibleTeamMemberException();
        }
        if (vinculos.find(teamId, userId).isPresent()) {
            throw new AlreadyTeamMemberException();
        }
        return vinculos.save(TeamMembership.member(teamId, userId));
    }

    @Transactional
    public void removeMember(CurrentUser ator, Long teamId, Long userId) {
        exigirGestaoDeMembros(acessar(ator, teamId));

        TeamMembership alvo = vinculoExistente(teamId, userId);
        if (alvo.isLead()) {
            exigirAdmin(ator);
        }
        vinculos.delete(alvo);
        eventos.publishEvent(new TeamMembershipRemoved(teamId, userId, ator.id(), relogio.instant()));
    }

    @Transactional
    public TeamMembership changeRole(CurrentUser ator, Long teamId, Long userId, TeamRole role) {
        acessar(ator, teamId);
        exigirAdmin(ator);

        TeamMembership alvo = vinculoExistente(teamId, userId);
        alvo.changeRole(role);
        // Salvo explicitamente, e nao deixado ao dirty checking: o contrato do repositorio
        // nao e JPA, e um adaptador que nao fosse JPA nao saberia que o objeto mudou.
        return vinculos.save(alvo);
    }

    /** A equipe existe. Sem regra de visibilidade: serve a configuracao feita por admin. */
    @Transactional(readOnly = true)
    public boolean exists(Long teamId) {
        return equipes.findById(teamId).isPresent();
    }

    /** Onde a pessoa participa e onde lidera, numa consulta so. */
    @Transactional(readOnly = true)
    public UserTeams teamsOf(Long userId) {
        List<TeamMembership> dela = vinculos.findByUser(userId);
        return new UserTeams(
                dela.stream().map(TeamMembership::getTeamId).collect(Collectors.toSet()),
                dela.stream().filter(TeamMembership::isLead).map(TeamMembership::getTeamId).collect(Collectors.toSet()));
    }

    /** Participa em qualquer papel. Ordem {@code (teamId, userId)}, como no repositorio. */
    @Transactional(readOnly = true)
    public boolean isMember(Long teamId, Long userId) {
        return vinculos.find(teamId, userId).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean isLead(Long teamId, Long userId) {
        return vinculos.find(teamId, userId).filter(TeamMembership::isLead).isPresent();
    }

    /**
     * A equipe, se o ator pode enxerga-la, e se ele gerencia os membros dela.
     *
     * <p>Inexistente e invisivel lancam a mesma excecao, com o mesmo corpo: quem esta de
     * fora nao consegue distinguir um caso do outro. Para admin o vinculo nem e consultado —
     * ele enxerga e gerencia qualquer equipe, participando dela ou nao.
     */
    private Acesso acessar(CurrentUser ator, Long teamId) {
        Team equipe = equipes.findById(teamId).orElseThrow(TeamNotFoundException::new);
        if (ator.isAdmin()) {
            return new Acesso(equipe, true);
        }
        TeamMembership doAtor = vinculos.find(teamId, ator.id()).orElseThrow(TeamNotFoundException::new);
        return new Acesso(equipe, doAtor.isLead());
    }

    private static void exigirGestaoDeMembros(Acesso acesso) {
        if (!acesso.gerenciaMembros()) {
            throw TeamActionForbiddenException.requiresAdminOrLead();
        }
    }

    private static void exigirAdmin(CurrentUser ator) {
        if (!ator.isAdmin()) {
            throw TeamActionForbiddenException.requiresAdmin();
        }
    }

    private TeamMembership vinculoExistente(Long teamId, Long userId) {
        return vinculos.find(teamId, userId).orElseThrow(TeamMembershipNotFoundException::new);
    }

    private record Acesso(Team equipe, boolean gerenciaMembros) {
    }
}
