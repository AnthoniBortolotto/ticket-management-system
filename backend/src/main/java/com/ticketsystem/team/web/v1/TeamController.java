package com.ticketsystem.team.web.v1;

import com.ticketsystem.auth.AuthFacade;
import com.ticketsystem.team.service.TeamService;
import com.ticketsystem.team.web.v1.dto.AddMemberRequest;
import com.ticketsystem.team.web.v1.dto.ChangeTeamRoleRequest;
import com.ticketsystem.team.web.v1.dto.CreateTeamRequest;
import com.ticketsystem.team.web.v1.dto.TeamMemberResponse;
import com.ticketsystem.team.web.v1.dto.TeamResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Equipes e seus membros.
 *
 * <p>Ao contrario de {@code /api/v1/users}, <strong>nao ha regra por URL no
 * {@code SecurityConfig}</strong> alem de exigir autenticacao. Quem pode gerenciar uma
 * equipe depende de liderar <em>aquela</em> equipe, o que so o banco responde — e a regra
 * inteira fica no {@code TeamService}, para nao ser metade la e metade aqui.
 *
 * <p>O controller so descobre quem pede, pela {@code AuthFacade}, e entrega ao service
 * junto com o resto. Nao ha listagem de equipes ainda: ela espera a decisao de paginacao
 * da Fase 5, como a de usuarios — devolver uma lista agora e passar a paginar depois
 * mudaria o formato da resposta, e isso ja seria uma {@code v2}.
 */
@RestController
@RequestMapping("/api/v1/teams")
class TeamController {

    private final TeamService service;
    private final AuthFacade auth;

    TeamController(TeamService service, AuthFacade auth) {
        this.service = service;
        this.auth = auth;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TeamResponse create(@Valid @RequestBody CreateTeamRequest pedido) {
        return TeamResponse.from(
                service.create(auth.currentUser(), pedido.name(), pedido.description()), List.of());
    }

    @GetMapping("/{teamId}")
    TeamResponse findById(@PathVariable Long teamId) {
        var detalhes = service.findById(auth.currentUser(), teamId);
        return TeamResponse.from(detalhes.team(), detalhes.members());
    }

    /** Entra sempre como {@code MEMBER}; liderar e um passo a parte, so de admin. */
    @PostMapping("/{teamId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    TeamMemberResponse addMember(@PathVariable Long teamId, @Valid @RequestBody AddMemberRequest pedido) {
        return TeamMemberResponse.from(service.addMember(auth.currentUser(), teamId, pedido.userId()));
    }

    /** Promove a {@code LEAD} ou rebaixa a {@code MEMBER}. Idempotente: repetir nao muda nada. */
    @PutMapping("/{teamId}/members/{userId}/role")
    TeamMemberResponse changeRole(
            @PathVariable Long teamId, @PathVariable Long userId, @Valid @RequestBody ChangeTeamRoleRequest pedido) {
        return TeamMemberResponse.from(service.changeRole(auth.currentUser(), teamId, userId, pedido.role()));
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeMember(@PathVariable Long teamId, @PathVariable Long userId) {
        service.removeMember(auth.currentUser(), teamId, userId);
    }
}
