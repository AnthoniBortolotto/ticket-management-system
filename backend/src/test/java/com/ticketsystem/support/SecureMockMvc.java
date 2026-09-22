package com.ticketsystem.support;

import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * {@link MockMvc} sobre o contexto inteiro, com a cadeia de filtros do Spring Security.
 *
 * <p>Existe porque no Boot 4 o {@code @AutoConfigureMockMvc} saiu de
 * {@code spring-boot-test-autoconfigure} para o modulo {@code spring-boot-webmvc-test}, que
 * nao esta no {@code pom.xml}. Montar a partir do contexto usa so o {@code spring-test} e o
 * {@code spring-security-test}, que ja existem — sem dependencia nova para economizar tres
 * linhas.
 *
 * <p>O {@code springSecurity()} e o que importa: sem ele o MockMvc chamaria o controller
 * direto, 401 e 403 simplesmente nao existiriam, e todo teste de seguranca passaria.
 */
public final class SecureMockMvc {

    private SecureMockMvc() {
    }

    public static MockMvc from(WebApplicationContext contexto) {
        return MockMvcBuilders.webAppContextSetup(contexto)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }
}
