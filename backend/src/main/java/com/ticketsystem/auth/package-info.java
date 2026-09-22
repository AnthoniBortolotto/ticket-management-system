/**
 * Autenticacao: confere credenciais, emite o token e decide quem esta pedindo.
 *
 * <p>Dono da <strong>sessao</strong>; {@code user} e dono da identidade e da credencial.
 * A divisao importa: este modulo nunca ve o hash de senha — ele pergunta
 * "esta senha confere?" a {@code UserFacade} e recebe so o resultado.
 *
 * <p>O token carrega apenas o subject e o papel global. Equipe <strong>nao</strong> entra:
 * o conteudo de um JWT so muda quando ele expira, e congelar vinculo de equipe numa
 * credencial significa que tirar alguem de uma equipe nao tem efeito imediato. A
 * visibilidade e resolvida por requisicao, contra o banco.
 *
 * <p>As entidades de {@code domain} guardam {@code Long userId} puro, sem
 * {@code @ManyToOne User}: referenciar classe interna de outro modulo quebraria o build.
 * A cascata e a do banco.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Autenticacao")
package com.ticketsystem.auth;
