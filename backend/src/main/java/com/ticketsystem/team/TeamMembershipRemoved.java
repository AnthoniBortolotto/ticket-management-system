package com.ticketsystem.team;

import java.time.Instant;

/**
 * Alguem saiu de uma equipe. Publicado pelo {@code TeamService} na mesma transacao que
 * apagou o vinculo.
 *
 * <p>{@code ticket} escuta para limpar o responsavel atual dos tickets daquela equipe que
 * estavam com a pessoa. {@code team} nao sabe que tickets existem — so conta o que
 * aconteceu, e e isso que mantem a dependencia num sentido so.
 *
 * <p>So identificadores, como todo evento: ele fica guardado, serializado, no arquivo de
 * publicacoes.
 *
 * @param teamId    a equipe
 * @param userId    quem saiu
 * @param removedBy quem removeu
 * @param removedAt quando, em UTC
 */
public record TeamMembershipRemoved(Long teamId, Long userId, Long removedBy, Instant removedAt) {
}
