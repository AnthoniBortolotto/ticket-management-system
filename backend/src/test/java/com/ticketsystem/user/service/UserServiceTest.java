package com.ticketsystem.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketsystem.common.error.ProblemKind;
import com.ticketsystem.user.UserRole;
import com.ticketsystem.user.domain.User;
import com.ticketsystem.user.domain.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/** As decisoes do modulo `user`, com repositorio e encoder mockados. */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String HASH =
            "$2a$10$TqZk9EsL91XXwsirPJRFpugEf.AsiMXF2h.Pr1z1FQ4GjfCwiHpBC";

    @Mock
    private UserRepository repositorio;

    @Mock
    private PasswordEncoder encoder;

    @InjectMocks
    private UserService service;

    @Test
    @DisplayName("credencial correta devolve a conta")
    void credencialCorretaDevolveAConta() {
        when(repositorio.findByEmail("ana@empresa.com"))
                .thenReturn(Optional.of(new User("ana@empresa.com", "Ana", HASH, UserRole.AGENT)));
        when(encoder.matches("senha-certa", HASH)).thenReturn(true);

        var conta = service.authenticate("ana@empresa.com", "senha-certa");

        assertThat(conta).isPresent();
        assertThat(conta.get().email()).isEqualTo("ana@empresa.com");
        assertThat(conta.get().role()).isEqualTo(UserRole.AGENT);
    }

    @Test
    @DisplayName("senha errada nao devolve conta")
    void senhaErradaNaoDevolveConta() {
        when(repositorio.findByEmail("ana@empresa.com"))
                .thenReturn(Optional.of(new User("ana@empresa.com", "Ana", HASH, UserRole.AGENT)));
        when(encoder.matches("senha-errada", HASH)).thenReturn(false);

        assertThat(service.authenticate("ana@empresa.com", "senha-errada")).isEmpty();
    }

    @Test
    @DisplayName("e-mail inexistente ainda assim paga o custo de verificar uma senha")
    void emailInexistenteAindaVerificaSenha() {
        when(repositorio.findByEmail("ninguem@empresa.com")).thenReturn(Optional.empty());

        assertThat(service.authenticate("ninguem@empresa.com", "qualquer")).isEmpty();

        // Sem esta verificacao contra um hash descartavel, o caminho "conta nao existe"
        // responde na hora e o caminho "senha errada" gasta ~80ms de BCrypt. A diferenca
        // de tempo entrega quais e-mails tem conta — enumeracao de usuarios por
        // cronometro, sem precisar de nenhuma resposta diferente.
        verify(encoder).matches(eq("qualquer"), anyString());
    }

    @Test
    @DisplayName("criar usuario grava a senha em hash, nunca em texto")
    void criarUsuarioGravaSenhaEmHash() {
        when(repositorio.existsByEmail("nova@empresa.com")).thenReturn(false);
        when(encoder.encode("senha-crua")).thenReturn(HASH);
        when(repositorio.save(any(User.class))).thenAnswer(chamada -> chamada.getArgument(0));

        var criada = service.create("nova@empresa.com", "Nova Pessoa", "senha-crua", UserRole.REQUESTER);

        verify(encoder).encode("senha-crua");
        // O retorno e o que o controller devolve ao admin. Sem afirmar sobre ele, um
        // `return null` passaria — foi o PITest que apontou.
        assertThat(criada.email()).isEqualTo("nova@empresa.com");
        assertThat(criada.fullName()).isEqualTo("Nova Pessoa");
        assertThat(criada.role()).isEqualTo(UserRole.REQUESTER);
    }

    @Test
    @DisplayName("senha de exatamente 72 bytes ainda cabe no BCrypt")
    void senhaNoLimiteDoBcryptEhAceita() {
        when(repositorio.existsByEmail(anyString())).thenReturn(false);
        when(encoder.encode(anyString())).thenReturn(HASH);
        when(repositorio.save(any(User.class))).thenAnswer(chamada -> chamada.getArgument(0));

        service.create("nova@empresa.com", "Nova", "a".repeat(72), UserRole.AGENT);

        verify(encoder).encode("a".repeat(72));
    }

    @Test
    @DisplayName("senha acima de 72 bytes e recusada como erro de entrada, nao como 500")
    void senhaAcimaDoLimiteDoBcryptEhRecusada() {
        when(repositorio.existsByEmail(anyString())).thenReturn(false);
        // 40 caracteres, 80 bytes em UTF-8. Um @Size(max = 72) no DTO contaria caracteres
        // e deixaria passar; o BCryptPasswordEncoder conta bytes e lanca
        // IllegalArgumentException — que chegaria ao cliente como erro interno.
        String acentuada = "ç".repeat(40);

        assertThatThrownBy(() -> service.create("nova@empresa.com", "Nova", acentuada, UserRole.AGENT))
                .isInstanceOf(PasswordTooLongException.class)
                .extracting(e -> ((PasswordTooLongException) e).kind())
                .isEqualTo(ProblemKind.INVALID);
        verify(encoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("e-mail ja usado nao cria outra conta")
    void emailDuplicadoNaoCriaConta() {
        when(repositorio.existsByEmail("ana@empresa.com")).thenReturn(true);

        assertThatThrownBy(
                        () -> service.create("ana@empresa.com", "Ana", "senha", UserRole.AGENT))
                .isInstanceOf(EmailAlreadyUsedException.class)
                .extracting(excecao -> ((EmailAlreadyUsedException) excecao).kind())
                .isEqualTo(ProblemKind.CONFLICT);

        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("a checagem de e-mail duplicado ignora a caixa do que foi digitado")
    void emailDuplicadoEhDetectadoIndependenteDeCaixa() {
        when(repositorio.existsByEmail("ANA@Empresa.com")).thenReturn(true);

        // O repositorio recebe o valor como veio; e ele que ignora a caixa. Se este
        // servico normalizasse antes, a regra existiria em dois lugares.
        assertThatThrownBy(
                        () -> service.create("ANA@Empresa.com", "Ana", "senha", UserRole.AGENT))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    @DisplayName("buscar por id devolve a conta")
    void buscarPorIdDevolveAConta() {
        when(repositorio.findById(7L))
                .thenReturn(Optional.of(new User("ana@empresa.com", "Ana", HASH, UserRole.ADMIN)));

        assertThat(service.findById(7L).role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("buscar id que nao existe e 404, nao nulo")
    void buscarIdInexistenteLancaNotFound() {
        when(repositorio.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(404L))
                .isInstanceOf(UserNotFoundException.class)
                .extracting(excecao -> ((UserNotFoundException) excecao).kind())
                .isEqualTo(ProblemKind.NOT_FOUND);
    }

    @Test
    @DisplayName("buscar por e-mail devolve a conta sem conferir senha nenhuma")
    void buscarPorEmailDevolveAConta() {
        when(repositorio.findByEmail("ana@empresa.com"))
                .thenReturn(Optional.of(new User("ana@empresa.com", "Ana", HASH, UserRole.AGENT)));

        assertThat(service.findByEmail("ana@empresa.com")).map(conta -> conta.email())
                .contains("ana@empresa.com");
        verify(encoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("buscar e-mail que nao existe devolve vazio")
    void buscarEmailInexistenteDevolveVazio() {
        when(repositorio.findByEmail("ninguem@empresa.com")).thenReturn(Optional.empty());

        assertThat(service.findByEmail("ninguem@empresa.com")).isEmpty();
    }

    @Test
    @DisplayName("a conta devolvida nao carrega o hash da senha")
    void contaDevolvidaNaoCarregaHash() {
        when(repositorio.findById(7L))
                .thenReturn(Optional.of(new User("ana@empresa.com", "Ana", HASH, UserRole.AGENT)));

        // UserAccount e o que atravessa a fronteira do modulo. Se o hash coubesse nele,
        // a fachada seria um vazamento com fachada em volta.
        assertThat(service.findById(7L).toString()).doesNotContain(HASH);
    }
}
