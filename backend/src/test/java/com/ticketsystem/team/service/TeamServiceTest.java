package com.ticketsystem.team.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketsystem.auth.CurrentUser;
import com.ticketsystem.common.error.DomainException;
import com.ticketsystem.common.error.ProblemKind;
import com.ticketsystem.support.TeamBuilder;
import com.ticketsystem.team.domain.Team;
import com.ticketsystem.team.domain.TeamMembership;
import com.ticketsystem.team.domain.TeamMembershipRepository;
import com.ticketsystem.team.domain.TeamRepository;
import com.ticketsystem.team.domain.TeamRole;
import com.ticketsystem.user.UserAccount;
import com.ticketsystem.user.UserFacade;
import com.ticketsystem.user.UserRole;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Quem pode fazer o que numa equipe, com repositorios e a fachada de usuarios mockados.
 *
 * <p>O elenco e fixo e cada teste diz so o que muda: um admin que nao participa da equipe,
 * um lider, um membro comum e um agente de fora. O ator chega como parametro, e nao lido
 * do contexto de seguranca — e isso que deixa estas regras testaveis sem Spring.
 */
@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    private static final CurrentUser ADMIN = new CurrentUser(1L, UserRole.ADMIN);
    private static final CurrentUser LIDER = new CurrentUser(10L, UserRole.AGENT);
    private static final CurrentUser MEMBRO = new CurrentUser(11L, UserRole.AGENT);
    private static final CurrentUser DE_FORA = new CurrentUser(12L, UserRole.AGENT);
    private static final Long NOVATO = 20L;

    @Mock
    private TeamRepository equipes;

    @Mock
    private TeamMembershipRepository vinculos;

    @Mock
    private UserFacade usuarios;

    @InjectMocks
    private TeamService service;

    private Team equipe;
    private Long equipeId;

    @BeforeEach
    void montarEquipe() {
        equipe = TeamBuilder.aTeam().named("Suporte N1").build();
        equipeId = equipe.getId();
    }

    @Nested
    @DisplayName("criar equipe")
    class Criar {

        @Test
        @DisplayName("admin cria a equipe")
        void adminCria() {
            when(equipes.existsByName("Suporte N2")).thenReturn(false);
            when(equipes.save(any(Team.class))).thenAnswer(chamada -> chamada.getArgument(0));

            Team criada = service.create(ADMIN, "Suporte N2", "Segundo nivel");

            assertThat(criada.getName()).isEqualTo("Suporte N2");
            assertThat(criada.getDescription()).isEqualTo("Segundo nivel");
        }

        @Test
        @DisplayName("nome repetido e 409, conferido ja sem os espacos das pontas")
        void nomeRepetidoEh409() {
            // O repositorio ignora a caixa; o service entrega o nome como a entidade o
            // guarda. Sem isso, " Suporte" passaria na checagem e estouraria no indice.
            when(equipes.existsByName("suporte n1")).thenReturn(true);

            assertThatThrownBy(() -> service.create(ADMIN, "  suporte n1  ", null))
                    .isInstanceOf(TeamNameAlreadyUsedException.class)
                    .satisfies(e -> assertThat(tipo(e)).isEqualTo(ProblemKind.CONFLICT));
            verify(equipes, never()).save(any());
        }

        @ParameterizedTest(name = "{0} nao cria equipe")
        @EnumSource(value = UserRole.class, names = {"AGENT", "REQUESTER"})
        @DisplayName("so admin cria equipe")
        void naoAdminNaoCria(UserRole papel) {
            assertThatThrownBy(() -> service.create(new CurrentUser(5L, papel), "Suporte N2", null))
                    .isInstanceOf(TeamActionForbiddenException.class)
                    .satisfies(e -> assertThat(tipo(e)).isEqualTo(ProblemKind.FORBIDDEN));
            // Recusado antes de consultar: nem a existencia do nome vaza.
            verify(equipes, never()).existsByName(anyString());
            verify(equipes, never()).save(any());
        }
    }

    @Nested
    @DisplayName("consultar equipe")
    class Consultar {

        @Test
        @DisplayName("membro ve a equipe e os vinculos")
        void membroVe() {
            existeEquipe();
            vinculoDoAtor(MEMBRO, TeamRole.MEMBER);
            List<TeamMembership> todos = List.of(vinculo(LIDER.id(), TeamRole.LEAD), vinculo(MEMBRO.id(), TeamRole.MEMBER));
            when(vinculos.findByTeam(equipeId)).thenReturn(todos);

            TeamDetails detalhes = service.findById(MEMBRO, equipeId);

            assertThat(detalhes.team()).isSameAs(equipe);
            assertThat(detalhes.members()).isEqualTo(todos);
        }

        @Test
        @DisplayName("admin ve qualquer equipe, mesmo sem participar dela")
        void adminVeSemParticipar() {
            existeEquipe();

            assertThat(service.findById(ADMIN, equipeId).team()).isSameAs(equipe);
        }

        @Test
        @DisplayName("quem nao participa recebe 404, igual a uma equipe que nao existe")
        void deForaRecebe404() {
            existeEquipe();

            // 404 e nao 403: 403 confirmaria que o id existe. Para quem esta de fora, uma
            // equipe invisivel e uma equipe inexistente tem que ser indistinguiveis.
            assertThatThrownBy(() -> service.findById(DE_FORA, equipeId))
                    .isInstanceOf(TeamNotFoundException.class)
                    .satisfies(e -> assertThat(tipo(e)).isEqualTo(ProblemKind.NOT_FOUND));
        }

        @Test
        @DisplayName("equipe que nao existe e 404, ate para admin")
        void inexistenteEh404() {
            when(equipes.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(ADMIN, 999L)).isInstanceOf(TeamNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("adicionar membro")
    class Adicionar {

        @Test
        @DisplayName("lider adiciona um agente, que entra como membro")
        void liderAdiciona() {
            existeEquipe();
            vinculoDoAtor(LIDER, TeamRole.LEAD);
            existeUsuario(NOVATO, UserRole.AGENT);
            when(vinculos.save(any(TeamMembership.class))).thenAnswer(chamada -> chamada.getArgument(0));

            TeamMembership novo = service.addMember(LIDER, equipeId, NOVATO);

            assertThat(novo.getTeamId()).isEqualTo(equipeId);
            assertThat(novo.getUserId()).isEqualTo(NOVATO);
            assertThat(novo.getRole()).isEqualTo(TeamRole.MEMBER);
        }

        @Test
        @DisplayName("admin adiciona sem participar da equipe")
        void adminAdiciona() {
            existeEquipe();
            existeUsuario(NOVATO, UserRole.AGENT);
            when(vinculos.save(any(TeamMembership.class))).thenAnswer(chamada -> chamada.getArgument(0));

            assertThat(service.addMember(ADMIN, equipeId, NOVATO).getUserId()).isEqualTo(NOVATO);
        }

        @Test
        @DisplayName("admin tambem pode ser membro de equipe")
        void adminPodeSerMembro() {
            // Admin ja enxerga tudo; o vinculo nao lhe da acesso novo. Recusar so atrapalharia
            // quem administra e tambem atende.
            existeEquipe();
            existeUsuario(NOVATO, UserRole.ADMIN);
            when(vinculos.save(any(TeamMembership.class))).thenAnswer(chamada -> chamada.getArgument(0));

            assertThat(service.addMember(ADMIN, equipeId, NOVATO).getUserId()).isEqualTo(NOVATO);
        }

        @Test
        @DisplayName("solicitante nao entra em equipe")
        void solicitanteNaoEntra() {
            // Membro enxerga os tickets da equipe e os comentarios internos deles. Um
            // solicitante numa equipe veria o que o CLAUDE.md diz que ele nunca ve.
            existeEquipe();
            existeUsuario(NOVATO, UserRole.REQUESTER);

            assertThatThrownBy(() -> service.addMember(ADMIN, equipeId, NOVATO))
                    .isInstanceOf(IneligibleTeamMemberException.class)
                    .satisfies(e -> assertThat(tipo(e)).isEqualTo(ProblemKind.CONFLICT));
            verify(vinculos, never()).save(any());
        }

        @Test
        @DisplayName("quem ja e membro nao entra de novo")
        void jaMembroEh409() {
            existeEquipe();
            existeUsuario(NOVATO, UserRole.AGENT);
            when(vinculos.find(equipeId, NOVATO)).thenReturn(Optional.of(vinculo(NOVATO, TeamRole.MEMBER)));

            assertThatThrownBy(() -> service.addMember(ADMIN, equipeId, NOVATO))
                    .isInstanceOf(AlreadyTeamMemberException.class)
                    .satisfies(e -> assertThat(tipo(e)).isEqualTo(ProblemKind.CONFLICT));
            verify(vinculos, never()).save(any());
        }

        @Test
        @DisplayName("membro comum nao adiciona ninguem")
        void membroComumNaoAdiciona() {
            existeEquipe();
            vinculoDoAtor(MEMBRO, TeamRole.MEMBER);

            assertThatThrownBy(() -> service.addMember(MEMBRO, equipeId, NOVATO))
                    .isInstanceOf(TeamActionForbiddenException.class)
                    .satisfies(e -> assertThat(tipo(e)).isEqualTo(ProblemKind.FORBIDDEN));
            // Recusado antes de olhar o alvo: quem nao gerencia nao descobre, pela
            // resposta, se um id de usuario existe ou que papel ele tem.
            verify(usuarios, never()).findById(anyLong());
            verify(vinculos, never()).save(any());
        }

        @Test
        @DisplayName("agente de fora recebe 404, nao 403")
        void deForaRecebe404() {
            existeEquipe();

            assertThatThrownBy(() -> service.addMember(DE_FORA, equipeId, NOVATO))
                    .isInstanceOf(TeamNotFoundException.class);
            verify(vinculos, never()).save(any());
        }
    }

    @Nested
    @DisplayName("remover membro")
    class Remover {

        @Test
        @DisplayName("lider remove um membro comum")
        void liderRemoveMembro() {
            existeEquipe();
            vinculoDoAtor(LIDER, TeamRole.LEAD);
            TeamMembership alvo = vinculo(NOVATO, TeamRole.MEMBER);
            when(vinculos.find(equipeId, NOVATO)).thenReturn(Optional.of(alvo));

            service.removeMember(LIDER, equipeId, NOVATO);

            verify(vinculos).delete(alvo);
        }

        @Test
        @DisplayName("lider nao remove outro lider")
        void liderNaoRemoveLider() {
            existeEquipe();
            vinculoDoAtor(LIDER, TeamRole.LEAD);
            when(vinculos.find(equipeId, NOVATO)).thenReturn(Optional.of(vinculo(NOVATO, TeamRole.LEAD)));

            assertThatThrownBy(() -> service.removeMember(LIDER, equipeId, NOVATO))
                    .isInstanceOf(TeamActionForbiddenException.class);
            verify(vinculos, never()).delete(any());
        }

        @Test
        @DisplayName("lider nao remove a si mesmo")
        void liderNaoRemoveASiMesmo() {
            // Sair da lideranca tambem e mexer em lideranca, e isso e decisao de admin.
            existeEquipe();
            vinculoDoAtor(LIDER, TeamRole.LEAD);

            assertThatThrownBy(() -> service.removeMember(LIDER, equipeId, LIDER.id()))
                    .isInstanceOf(TeamActionForbiddenException.class);
            verify(vinculos, never()).delete(any());
        }

        @Test
        @DisplayName("admin remove um lider")
        void adminRemoveLider() {
            existeEquipe();
            TeamMembership alvo = vinculo(NOVATO, TeamRole.LEAD);
            when(vinculos.find(equipeId, NOVATO)).thenReturn(Optional.of(alvo));

            service.removeMember(ADMIN, equipeId, NOVATO);

            verify(vinculos).delete(alvo);
        }

        @Test
        @DisplayName("membro comum nao remove ninguem, nem descobre quem e membro")
        void membroComumNaoRemove() {
            existeEquipe();
            vinculoDoAtor(MEMBRO, TeamRole.MEMBER);

            assertThatThrownBy(() -> service.removeMember(MEMBRO, equipeId, NOVATO))
                    .isInstanceOf(TeamActionForbiddenException.class);
            verify(vinculos, never()).find(equipeId, NOVATO);
            verify(vinculos, never()).delete(any());
        }

        @Test
        @DisplayName("remover quem nao participa e 404")
        void removerQuemNaoParticipaEh404() {
            existeEquipe();

            assertThatThrownBy(() -> service.removeMember(ADMIN, equipeId, NOVATO))
                    .isInstanceOf(TeamMembershipNotFoundException.class)
                    .satisfies(e -> assertThat(tipo(e)).isEqualTo(ProblemKind.NOT_FOUND));
        }

        @Test
        @DisplayName("agente de fora recebe 404")
        void deForaRecebe404() {
            existeEquipe();

            assertThatThrownBy(() -> service.removeMember(DE_FORA, equipeId, NOVATO))
                    .isInstanceOf(TeamNotFoundException.class);
            verify(vinculos, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("mudar papel na equipe")
    class MudarPapel {

        @ParameterizedTest(name = "admin muda para {0}")
        @EnumSource(TeamRole.class)
        @DisplayName("admin promove e rebaixa")
        void adminMudaPapel(TeamRole papel) {
            existeEquipe();
            TeamMembership alvo = vinculo(NOVATO, papel == TeamRole.LEAD ? TeamRole.MEMBER : TeamRole.LEAD);
            when(vinculos.find(equipeId, NOVATO)).thenReturn(Optional.of(alvo));
            when(vinculos.save(alvo)).thenReturn(alvo);

            assertThat(service.changeRole(ADMIN, equipeId, NOVATO, papel).getRole()).isEqualTo(papel);
            // Salvo explicitamente, e nao por dirty checking: o contrato do repositorio nao
            // e JPA, e um adaptador que nao seja nao saberia que o objeto mudou.
            verify(vinculos).save(alvo);
        }

        @Test
        @DisplayName("lider nao promove ninguem")
        void liderNaoPromove() {
            // Lideranca amplia visibilidade — o lider ve os tickets que sairam da equipe.
            // Concede-la e decisao de admin, nao de um par.
            existeEquipe();
            vinculoDoAtor(LIDER, TeamRole.LEAD);

            assertThatThrownBy(() -> service.changeRole(LIDER, equipeId, NOVATO, TeamRole.LEAD))
                    .isInstanceOf(TeamActionForbiddenException.class);
            verify(vinculos, never()).save(any());
        }

        @Test
        @DisplayName("mudar o papel de quem nao participa e 404")
        void naoParticipaEh404() {
            existeEquipe();

            assertThatThrownBy(() -> service.changeRole(ADMIN, equipeId, NOVATO, TeamRole.LEAD))
                    .isInstanceOf(TeamMembershipNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("perguntas da fachada")
    class Perguntas {

        @Test
        @DisplayName("lider e membro e lider")
        void liderEhMembroELider() {
            when(vinculos.find(equipeId, LIDER.id())).thenReturn(Optional.of(vinculo(LIDER.id(), TeamRole.LEAD)));

            assertThat(service.isMember(equipeId, LIDER.id())).isTrue();
            assertThat(service.isLead(equipeId, LIDER.id())).isTrue();
        }

        @Test
        @DisplayName("membro comum e membro e nao lider")
        void membroNaoEhLider() {
            when(vinculos.find(equipeId, MEMBRO.id())).thenReturn(Optional.of(vinculo(MEMBRO.id(), TeamRole.MEMBER)));

            assertThat(service.isMember(equipeId, MEMBRO.id())).isTrue();
            assertThat(service.isLead(equipeId, MEMBRO.id())).isFalse();
        }

        @Test
        @DisplayName("existencia de equipe nao depende de quem pergunta")
        void existencia() {
            // Para o roteamento de tickets, que e configuracao de admin: a pergunta e so se o
            // id aponta para uma equipe, sem regra de visibilidade no meio.
            existeEquipe();
            when(equipes.findById(999L)).thenReturn(Optional.empty());

            assertThat(service.exists(equipeId)).isTrue();
            assertThat(service.exists(999L)).isFalse();
        }

        @Test
        @DisplayName("sem vinculo nao e nem membro nem lider")
        void semVinculo() {
            assertThat(service.isMember(equipeId, DE_FORA.id())).isFalse();
            assertThat(service.isLead(equipeId, DE_FORA.id())).isFalse();
        }
    }

    private void existeEquipe() {
        when(equipes.findById(equipeId)).thenReturn(Optional.of(equipe));
    }

    private void vinculoDoAtor(CurrentUser ator, TeamRole papel) {
        when(vinculos.find(equipeId, ator.id())).thenReturn(Optional.of(vinculo(ator.id(), papel)));
    }

    private void existeUsuario(Long id, UserRole papel) {
        when(usuarios.findById(id)).thenReturn(new UserAccount(id, "pessoa" + id + "@exemplo.com", "Pessoa", papel));
    }

    private TeamMembership vinculo(Long userId, TeamRole papel) {
        return TeamBuilder.membership(equipeId, userId, papel);
    }

    private static ProblemKind tipo(Throwable excecao) {
        return ((DomainException) excecao).kind();
    }
}
