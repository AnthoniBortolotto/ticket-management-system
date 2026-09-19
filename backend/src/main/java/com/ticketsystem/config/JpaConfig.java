package com.ticketsystem.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Liga o preenchimento automatico de {@code createdAt} e {@code updatedAt} em
 * {@link com.ticketsystem.common.domain.BaseEntity}.
 */
@Configuration
@EnableJpaAuditing
class JpaConfig {
}
