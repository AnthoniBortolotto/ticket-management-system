package com.ticketsystem.user.web.v1;

import com.ticketsystem.user.service.UserService;
import com.ticketsystem.user.web.v1.dto.CreateUserRequest;
import com.ticketsystem.user.web.v1.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestao de usuarios, restrita a administrador.
 *
 * <p>A restricao mora no {@code SecurityConfig}, por URL: toda rota sob
 * {@code /api/v1/users} exige {@code ROLE_ADMIN}. Nao ha {@code @PreAuthorize} aqui para
 * a regra nao existir em dois lugares que podem divergir.
 *
 * <p>Versao minima, de proposito: criar e consultar. E o que destrava a Fase 3 — sem isto
 * o unico usuario do sistema seria o admin semeado — e o que da um endpoint protegido por
 * papel de verdade para o teste de 403. Listagem paginada fica para quando a decisao sobre
 * paginacao for tomada, junto com a de tickets.
 */
@RestController
@RequestMapping("/api/v1/users")
class UserController {

    private final UserService service;

    UserController(UserService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse create(@Valid @RequestBody CreateUserRequest pedido) {
        return UserResponse.from(
                service.create(pedido.email(), pedido.fullName(), pedido.password(), pedido.role()));
    }

    @GetMapping("/{id}")
    UserResponse findById(@PathVariable Long id) {
        return UserResponse.from(service.findById(id));
    }
}
