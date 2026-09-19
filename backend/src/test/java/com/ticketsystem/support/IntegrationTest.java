package com.ticketsystem.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Sobe a aplicacao inteira contra o Postgres de teste.
 *
 * <p>Use isto so quando o teste precisa de varios modulos juntos. Para exercitar um
 * modulo isolado, prefira {@code @ApplicationModuleTest}: sobe bem menos contexto.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresContainer.class)
public @interface IntegrationTest {
}
