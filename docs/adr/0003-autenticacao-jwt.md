# ADR 0003 — Autenticação: JWT HS256 com sessão revogável em banco

- **Status:** aceito
- **Data:** 2026-09-22

## Contexto

Toda regra de visibilidade do sistema começa em "quem está pedindo?". Sem identidade
autenticada, não há como escrever o teste de caso negativo que o CLAUDE.md exige de
cada regra — então autenticação é pré-requisito de todas as fases seguintes.

Quatro perguntas tinham respostas plausíveis nos dois sentidos, e cada escolha vira
contrato:

1. O que o token carrega.
2. Como a assinatura é feita.
3. Como uma sessão é encerrada antes de expirar.
4. Como o login resiste a força bruta.

## Decisão

### O token carrega apenas `sub` e `role`

O access token é um JWT com o id do usuário (`sub`), o papel global (`role`), emissor,
emissão, validade e um `jti`. **As equipes não entram**, embora o roadmap as previsse.

O motivo é de segurança, não de tamanho. O conteúdo de um JWT só muda quando ele expira:
com as equipes no token, tirar alguém de uma equipe continuaria dando acesso aos tickets
dela até o vencimento. A visibilidade é a parte mais cara de errar do sistema — listar
ticket de outra equipe é vazamento de dados —, então ela é resolvida por requisição,
contra o banco, via `TeamFacade` (Fase 3). O custo é uma consulta indexada por requisição
que precise de equipe, que é o custo de qualquer autorização confiável.

O papel também é relido do banco a cada renovação de sessão, e não herdado do token
anterior: uma promoção ou um rebaixamento vale no próximo refresh, sem novo login.

### HS256 com chave simétrica de `JWT_SECRET`

Quem emite e quem valida o token é o mesmo serviço. Assinatura assimétrica (RS256)
resolveria um problema que não existe aqui — distribuir a chave de verificação para
terceiros — e acrescentaria geração, guarda e rotação de um par de chaves. Se um dia
outro serviço precisar validar o token, a troca é para RS256 com JWKS.

A chave é lida como UTF-8 cru, com mínimo de 32 bytes (o que o HS256 exige). **Não há
default no código**, e a configuração recusa no boot: valor vazio, valor curto e o texto
literal `${JWT_SECRET}`. Este último porque o Spring não reclama de variável de ambiente
ausente — ele deixa o placeholder como valor, e a aplicação subiria assinando tokens com
essa string pública como chave. É a mesma armadilha que quase semeou o admin com a senha
`${ADMIN_PASSWORD_HASH}` na Fase 1.

A validação usa o resource server do Spring Security
(`spring-boot-starter-security-oauth2-resource-server`), e não um filtro escrito à mão:
assinatura, algoritmo travado em HS256 e validade com tolerância de relógio já vêm
resolvidos e testados. **O nome tem `security-` no meio** — no Boot 4 o artefato
`spring-boot-starter-oauth2-resource-server` está deprecado, e continua resolvendo, então
o build não avisa.

### Sessão revogável: refresh token opaco, em banco, com rotação

O access token dura pouco (60 minutos por padrão) e não é revogável — é o preço de um
token que se valida sozinho. A sessão longa é um **refresh token opaco de 256 bits**, e
não um segundo JWT: um JWT se valida sozinho, que é o oposto de revogável, e ele
precisaria estar em banco de qualquer forma.

- **Guardado pelo SHA-256, nunca pelo valor.** Quem ler um dump da tabela não consegue
  usar o que leu. SHA-256 e não BCrypt, porque 256 bits aleatórios não têm o que forçar
  bruta, e o sal por linha do BCrypt impediria buscar o token por igualdade. Um `CHECK`
  no banco recusa qualquer coisa que não seja hexadecimal de 64 caracteres — gravar o
  valor cru por engano funcionaria em todos os fluxos, e é por isso que precisa ser
  recusado.
- **Rotação a cada renovação**, na mesma família. Apresentar um token já usado só pode
  significar uma cópia: a família inteira é revogada, com motivo `REUSE_DETECTED`, para
  quem roubou e quem foi roubado perderem o acesso juntos. Revogar só a linha
  apresentada não adiantaria — o token que o ladrão já trocou continuaria vivo.
- **Família, e não "todas as sessões do usuário"**: um roubo detectado derruba a linhagem
  comprometida, e não todos os dispositivos da pessoa.
- **Trava pessimista na renovação**: sem ela, duas renovações simultâneas do mesmo token
  emitiriam dois tokens novos, e a detecção de reuso nunca dispararia.

### Força bruta: bloqueio por conta, persistido

Depois de 5 falhas seguidas (configurável), a conta fica bloqueada por 15 minutos e
recebe **423**. O bloqueio é consultado **antes** da verificação de senha: senão a senha
certa destravaria uma conta bloqueada, e cada tentativa durante o bloqueio continuaria
custando um BCrypt ao servidor. Falhas mais antigas que a janela do bloqueio são
esquecidas — o contador significa "falhas seguidas e recentes".

Persistido em tabela própria (`login_lockouts`), e não em memória nem em colunas de
`users`:

- em memória, o contador zera a cada restart e cada instância conta as suas;
- em `users`, o `updated_at` passaria a significar "quando alguém errou a senha", e
  tráfego anônimo escreveria na linha do cadastro.

O incremento é um `INSERT … ON CONFLICT DO UPDATE` atômico — um find-and-save perderia
contagem quando duas tentativas falham ao mesmo tempo.

### Swagger e `/v3/api-docs` continuam públicos

É projeto de portfólio: o contrato navegável sem token é parte da demonstração, e ele não
expõe dado nenhum, só o formato das requisições. Fora de desenvolvimento, restringir é
uma linha em `SecurityConfig.PUBLIC_PATHS` condicionada a perfil. Um teste fixa a
decisão atual.

## Consequências

**Dois mecanismos dependem de transação, e ambos quebrariam em silêncio.** A falha
contada no login e a revogação feita num replay acontecem logo antes de uma exceção.
Numa transação só, o rollback provocado pela exceção desfaria as duas — o bloqueio nunca
dispararia, e o token do ladrão continuaria vivo, com a suíte verde se o teste olhasse
só a resposta. Por isso o contador grava em `REQUIRES_NEW`, a renovação declara
`noRollbackFor`, e o `AuthService` **não tem `@Transactional` de propósito**. Os testes
de integração olham o banco, e foi confirmado sabotando cada proteção que eles quebram.

**A resposta 423 revela que o e-mail tem conta.** Para chegar nela é preciso errar a
senha daquela conta várias vezes seguidas, e a pessoa legítima precisa saber por que não
consegue entrar. Custo aceito. Senha errada e e-mail inexistente, esses sim, recebem
resposta idêntica — mesmo status, mesmo corpo — e o caminho do e-mail inexistente
verifica a senha contra um hash descartável para não se distinguir pelo tempo.

**Bloqueio por conta é vetor de negação de serviço.** Quem souber o e-mail de alguém
pode mantê-lo bloqueado errando a senha — e o e-mail do admin está publicado no README.
A janela curta, que vence sozinha e não é estendida por tentativas durante o bloqueio,
limita o estrago. Limite por IP no ingress é o complemento natural, e é decisão de
infraestrutura, não de aplicação.

**`refresh_tokens` cresce sem limite** e guarda sessões expiradas. Mesma categoria do
`event_publication_archive`: sem expurgo nesta fase, com a consulta anotada no cabeçalho
da migration `V4`.

**Access token não é revogável.** Logout e reuso encerram a sessão, mas um access token
já emitido vale até o fim dos seus 60 minutos. É o que o torna verificável sem ir ao
banco; se a janela incomodar, a alavanca é o TTL.

## Alternativas descartadas

| Alternativa | Por que não |
|---|---|
| Equipes no token | Janela de vazamento do tamanho da validade do token. |
| RS256 com par de chaves | Resolve distribuição de chave de verificação, que não existe aqui. |
| Refresh token como JWT | Autovalidável é o oposto de revogável. |
| Refresh token guardado em BCrypt | Sal por linha impede buscar por igualdade; 256 bits não precisam de custo artificial. |
| Contador de falhas em memória | Zera no restart, dobra o limite com duas instâncias. |
| Contador por e-mail, não por conta | Tráfego anônimo criaria linhas para endereços inventados, sem limite. |
| Filtro de JWT escrito à mão | Reescreveria o que o resource server já faz e testa. |
