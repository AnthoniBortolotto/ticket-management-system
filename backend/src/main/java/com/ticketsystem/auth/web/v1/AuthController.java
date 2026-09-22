package com.ticketsystem.auth.web.v1;

import com.ticketsystem.auth.service.AuthService;
import com.ticketsystem.auth.web.v1.dto.LoginRequest;
import com.ticketsystem.auth.web.v1.dto.RefreshRequest;
import com.ticketsystem.auth.web.v1.dto.TokenResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login, renovacao e logout.
 *
 * <p>Os tres sao publicos — quem os chama pode estar com o access token expirado; a
 * credencial e o que vem no corpo. O {@code OpenApiConfig} exige bearer em toda operacao,
 * e o {@code @SecurityRequirements} vazio e o que tira estas tres do requisito no schema
 * publicado. Sem ele o contrato mentiria, e o cliente gerado a partir dele mandaria token
 * para fazer login.
 *
 * <p>Sem regra de negocio aqui: recebe DTO, delega, devolve DTO. Erro vira excecao de
 * dominio e o {@code GlobalExceptionHandler} traduz.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthService service;

    AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    @SecurityRequirements
    TokenResponse login(@Valid @RequestBody LoginRequest pedido) {
        return TokenResponse.from(service.login(pedido.email(), pedido.password()));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    TokenResponse refresh(@Valid @RequestBody RefreshRequest pedido) {
        return TokenResponse.from(service.refresh(pedido.refreshToken()));
    }

    /** 204 mesmo para token desconhecido: responder diferente diria se o token existia. */
    @PostMapping("/logout")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody RefreshRequest pedido) {
        service.logout(pedido.refreshToken());
    }
}
