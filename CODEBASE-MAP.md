# CODEBASE-MAP

Índice do repositório: onde cada coisa mora e o que ela faz.

O documento tem **duas partes, e elas não se misturam**:

- **[Parte 1 — O que existe hoje](#parte-1--o-que-existe-hoje)** é o índice de busca.
  Tudo listado ali está no disco agora.
- **[Parte 2 — Estrutura planejada](#parte-2--estrutura-planejada)** é o desenho
  acordado. **Nada dali existe ainda.**

Quando um arquivo passa a existir, a linha dele **muda de parte**. As regras de
atualização estão em
[CLAUDE.md](CLAUDE.md#regra-permanente-manter-o-codebase-mapmd-atualizado).

---

# Parte 1 — O que existe hoje

**Estado do repositório: identidade, equipes e tickets prontos, com visibilidade e
atribuição; SLA, auditoria e telas ainda não.** Backend compila, sobe e passa
`./mvnw verify`; frontend passa `lint`, `typecheck`, `test` e `build`; a stack tem Compose e
manifests de Kubernetes. Existem os módulos `user`, `auth`, `team` e `ticket` — abrir
chamado roteado pela categoria, listar filtrando no banco pela regra de visibilidade, mover
pelo fluxo, conversar, e atribuir: responsável atual, modo exclusivo, devolução e
transferência. **Não há SLA, auditoria nem tela.**

**Não há CI.** Toda verificação é manual: rode os comandos de
[CLAUDE.md](CLAUDE.md#comandos) antes de commitar. Em particular, **as imagens Docker
nunca foram construídas** — ver a nota em `docker/` abaixo.

**Legenda:** ✅ pronto · 🚧 em andamento. Só aparecem na Parte 1 — na Parte 2 tudo é
planejado por definição, e marcar isso linha a linha seria ruído.

## Raiz

| Caminho | Status | O que é |
|---|---|---|
| `README.md` | ✅ | Visão geral do produto, modelo de domínio e como rodar. |
| `CLAUDE.md` | ✅ | Regras de trabalho, convenções e regras de domínio invioláveis. |
| `CODEBASE-MAP.md` | ✅ | Este arquivo. |
| `LICENSE` | ✅ | Licença do projeto. |
| `.gitignore` | ✅ | Ignora artefatos de Node, Java/Maven, Playwright, IDEs e SO. Versiona de propósito o wrapper do Maven, os `.env.example` e os arquivos compartilhados do VS Code. |
| `.dockerignore` | ✅ | Enxuga o contexto de build, que é a raiz do repositório para os dois Dockerfiles. |

## Backend — `backend/`

Maven padrão, pacote raiz `com.ticketsystem`, organização por feature. As fronteiras são
verificadas pelo Spring Modulith: raiz do pacote é público, subpacote é interno.

| Caminho | Status | O que faz |
|---|---|---|
| `pom.xml` | ✅ | Boot 4.1.1 sobre Java 25, Modulith com registro de eventos em JPA, resource server do Spring Security para o JWT, springdoc, Flyway, Testcontainers, JaCoCo com threshold em domain/service e PITest. Faz `spring-boot:run` subir no perfil `dev`. Os comentários dele registram as armadilhas de versão. |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/` | ✅ | Wrapper do Maven 3.9.12: o build funciona sem Maven instalado. |
| `.env.example` | ✅ | Modelo das variáveis. **Copie para `backend/.env` antes da primeira execução:** `ADMIN_PASSWORD_HASH` e `JWT_SECRET` não têm default, e sem elas o boot para. As demais têm default apontando para o Postgres do Compose. |

### `src/main/java/com/ticketsystem/`

| Caminho | Status | O que faz |
|---|---|---|
| `TicketSystemApplication.java` | ✅ | Entrypoint do Spring Boot. |
| `config/package-info.java` | ✅ | Declara `config` como módulo aberto do Modulith. |
| `config/SecurityConfig.java` | ✅ | Cadeia de filtros stateless: valida o bearer token em cada requisição, libera health, documentação e os três endpoints de sessão (um a um, nunca por curinga), exige `ADMIN` em `/api/v1/users` e `/api/v1/ticket-routes` e nega o resto. Devolve 401/403 ao advice, para saírem em `ProblemDetail`. Declara o `PasswordEncoder` — BCrypt puro, por causa do CHECK da V2. |
| `config/TimeConfig.java` | ✅ | Publica o `Clock` em UTC, para expiração e bloqueio serem testáveis sem dormir. |
| `config/JacksonConfig.java` | ✅ | Força data em ISO-8601. Existe como classe porque no Jackson 3 a flag mudou de enum e o Boot 4 não expõe propriedade para ela. |
| `config/OpenApiConfig.java` | ✅ | Metadados e esquema de segurança do schema em `/v3/api-docs`, de onde saem os tipos do frontend. |
| `config/JpaConfig.java` | ✅ | Liga a auditoria do Spring Data que preenche `createdAt` e `updatedAt`, com o instante do `Clock` truncado em microssegundos — a precisão do Postgres, para o `POST` e o `GET` devolverem o mesmo valor. |
| `common/package-info.java` | ✅ | Declara `common` como módulo aberto do Modulith. |
| `common/error/FieldProblem.java` | ✅ | Um campo rejeitado pela validação e o motivo. Vai na propriedade `errors` do `ProblemDetail`. |
| `common/error/GlobalExceptionHandler.java` | ✅ | Traduz exceção em resposta HTTP no formato `ProblemDetail` (RFC 9457). Herda de `ResponseEntityExceptionHandler`, então 404, 405 e 415 já saem padronizados; traduz qualquer `DomainException` pelo `ProblemKind`, gravação concorrente perdida (`@Version`) em 409 e 401/403 do Spring Security sem vazar a mensagem interna. |
| `common/error/ProblemKind.java` | ✅ | Que tipo de problema aconteceu — inválido, não autenticado, proibido, não encontrado, conflito, bloqueado — e o status HTTP de cada um. |
| `common/error/DomainException.java` | ✅ | Base das exceções de todos os módulos. É o que deixa o advice único sem `common` importar módulo nenhum: o import inverso fecharia ciclo. |
| `common/web/PageResponse.java` | ✅ | O formato de página de toda listagem da API: `content`, `page` (a partir de zero), `size`, `totalElements`, `totalPages`. Record próprio, e não o `Page` do Spring Data serializado. |
| `common/domain/BaseEntity.java` | ✅ | Id e carimbos de tempo em UTC, com igualdade por id. |
| `sla/`, `audit/` | 🚧 | Só `package-info.java` com javadoc descrevendo o módulo. **A anotação `@ApplicationModule` entra junto com a primeira classe** — anotar pacote vazio quebra o build. |
| `user/package-info.java` | ✅ | Declara o módulo `user` e registra que o hash de senha não sai dele. |
| `user/UserRole.java` | ✅ | Papel global `REQUESTER`, `AGENT`, `ADMIN`. Na raiz, e não em `domain/`: `auth` precisa dele para o claim do token, e subpacote é interno. |
| `user/UserAccount.java` | ✅ | O que os outros módulos podem saber de um usuário: id, e-mail, nome e papel — nunca o hash. |
| `user/UserFacade.java` | ✅ | Única porta do módulo: confere credencial, busca por e-mail e por id. |
| `user/domain/User.java` | ✅ | Entidade do usuário. Normaliza o e-mail em minúsculas sem depender do idioma da máquina e esconde o hash do `toString`. |
| `user/domain/UserRepository.java` | ✅ | Porta de persistência em linguagem de negócio. A busca por e-mail ignora a caixa. |
| `user/service/UserService.java` | ✅ | Único lugar que toca no `PasswordEncoder`. Confere senha — contra hash descartável quando o e-mail não existe, para o tempo não enumerar contas — e cria usuário recusando senha acima de 72 bytes. |
| `user/service/*Exception.java` | ✅ | `EmailAlreadyUsed` (409), `UserNotFound` (404) e `PasswordTooLong` (400). |
| `user/infra/SpringDataUserRepository.java`, `JpaUserRepository.java` | ✅ | Spring Data e o adaptador que o traduz para a porta de domínio. |
| `user/web/v1/UserController.java` + `dto/` | ✅ | `POST /api/v1/users` e `GET /api/v1/users/{id}`, só para admin. A listagem ainda não existe; quando entrar, segue o `PageResponse`. |
| `auth/package-info.java` | ✅ | Declara o módulo `auth` e registra por que o token não carrega equipes. |
| `auth/AuthFacade.java`, `auth/CurrentUser.java` | ✅ | Única porta do módulo: quem está pedindo, com id e papel global. O controller obtém e entrega ao service, que recebe o ator como parâmetro e não lê o contexto de segurança. |
| `auth/domain/LockoutPolicy.java` | ✅ | Quantas falhas seguidas bloqueiam a conta e por quanto tempo. Regra pura, com o instante vindo de fora. |
| `auth/domain/LoginLockout.java`, `LoginLockoutRepository.java` | ✅ | O estado de falhas de uma conta e a porta de escrita atômica. Não é entidade JPA, de propósito. |
| `auth/domain/RefreshToken.java`, `RevocationReason.java`, `RefreshTokenRepository.java` | ✅ | Sessão renovável guardada pelo SHA-256: rotação, revogação por família e a trava pessimista da renovação. |
| `auth/service/AuthProperties.java` | ✅ | Configuração do login. Recusa no boot segredo vazio, curto ou o texto literal `${JWT_SECRET}`. |
| `auth/service/JwtService.java` | ✅ | Emite o access token com `sub`, `role` e `jti` — sem equipes. |
| `auth/service/LoginLockoutService.java` | ✅ | Conta a falha em transação própria, para a exceção do login não desfazer a contagem. |
| `auth/service/RefreshTokenService.java` | ✅ | Emite, rotaciona e revoga. Num replay revoga a família inteira, e a exceção que vem depois não desfaz isso. |
| `auth/service/AuthService.java` | ✅ | A ordem do login: bloqueio antes da senha, mesma resposta para senha errada e conta inexistente. Sem `@Transactional` de propósito. |
| `auth/service/Tokens.java`, `IssuedRefreshToken.java` e as 4 exceções | ✅ | Valores que não imprimem credencial no `toString`; `InvalidCredentials`, `InvalidRefreshToken` e `NotAuthenticated` (401) e `AccountLocked` (423). |
| `auth/infra/JwtConfig.java` | ✅ | Chave HS256, encoder, decoder travado no algoritmo e conversão do claim `role` em `ROLE_*`. Loga que subiu. |
| `auth/infra/JwtCurrentUserResolver.java` | ✅ | Desfaz a conversão do `JwtConfig`: lê o id do `sub` e o papel das mesmas authorities que o `hasRole` enxerga. Qualquer dúvida — anônimo, `sub` não numérico, papel desconhecido ou repetido — vira 401, nunca um default. |
| `auth/infra/JdbcLoginLockoutRepository.java` | ✅ | Contador de falhas com `INSERT … ON CONFLICT DO UPDATE` atômico, que recomeça após bloqueio vencido ou falha antiga. |
| `auth/infra/SpringDataRefreshTokenRepository.java`, `JpaRefreshTokenRepository.java` | ✅ | Refresh tokens em JPA. A busca para renovar exige transação aberta, senão a trava não valeria. |
| `auth/web/v1/AuthController.java` + `dto/` | ✅ | `POST /api/v1/auth/login`, `/refresh` e `/logout`, públicos também no contrato OpenAPI. |
| `team/package-info.java` | ✅ | Declara o módulo `team` e registra que ele depende de `user` e `auth`, sem ciclo. |
| `team/TeamFacade.java` | ✅ | Única porta do módulo: "participa desta equipe?", "lidera?", "existe?" e "de quais participa e quais lidera", lidos do banco a cada chamada. Ordem `(teamId, userId)` em todo o módulo. |
| `team/TeamMembershipRemoved.java` | ✅ | Evento de saída de alguém da equipe, publicado na transação que apaga o vínculo. `ticket` escuta para limpar o responsável atual; `team` não sabe que tickets existem. |
| `team/UserTeams.java` | ✅ | As equipes de uma pessoa — onde participa e onde lidera —, numa consulta só, para o filtro da listagem de tickets. |
| `team/domain/Team.java` | ✅ | Entidade da equipe. Apara o nome e guarda a caixa digitada; descrição em branco vira ausente. |
| `team/domain/TeamMembership.java`, `TeamRole.java` | ✅ | Vínculo de um usuário com uma equipe, papel `MEMBER` ou `LEAD`. Sempre nasce `MEMBER`; as duas pontas não mudam depois de gravadas. |
| `team/domain/TeamRepository.java`, `TeamMembershipRepository.java` | ✅ | Portas de persistência. A busca por nome ignora a caixa, como o índice único da V2. |
| `team/service/TeamService.java` | ✅ | Toda a regra de quem gerencia equipe: não-membro recebe 404, como se a equipe não existisse; membro comum, 403. `MEMBER` é gerenciado por admin ou pelo `LEAD` da equipe. Criar equipe e mexer em liderança é só de admin. Solicitante não entra em equipe. Publica `TeamMembershipRemoved` na remoção. |
| `team/service/TeamDetails.java` e as 6 exceções | ✅ | Equipe com os vínculos; `TeamNotFound` e `TeamMembershipNotFound` (404), `TeamActionForbidden` (403), `TeamNameAlreadyUsed`, `AlreadyTeamMember` e `IneligibleTeamMember` (409). |
| `team/infra/` | ✅ | Spring Data e os dois adaptadores para as portas de domínio. |
| `team/web/v1/TeamController.java` + `dto/` | ✅ | `POST /api/v1/teams`, `GET /api/v1/teams/{id}`, `POST .../members`, `PUT .../members/{userId}/role` e `DELETE .../members/{userId}`. Sem regra no `SecurityConfig`: ela depende de liderar aquela equipe e mora inteira no service. Sem listagem até a decisão de paginação. |
| `ticket/package-info.java` | ✅ | Declara o módulo `ticket` e registra que ele depende de `team` e `auth`, que não sabem que tickets existem. |
| `ticket/TicketStatus.java` | ✅ | Os status **e** as transições permitidas: o diagrama do README mais `IN_PROGRESS → RESOLVED`. Nada volta para `OPEN`, nada transiciona para si mesmo. Na raiz porque viaja no evento; incluído à mão no JaCoCo e no PITest, que só olham `domain`/`service`. |
| `ticket/TicketStatusChanged.java` | ✅ | Evento de cada transição — de, para, quem e quando, só identificadores. Publicado na mesma transação que grava; `sla` e `audit` vão escutar. |
| `ticket/TicketAssignment.java`, `TicketAssignmentChanged.java` | ✅ | A fotografia dos quatro campos de atribuição, e o evento de toda reatribuição, com o antes e o depois inteiros — só identificadores. |
| `ticket/domain/Ticket.java` | ✅ | O chamado. Nasce `OPEN` em modo equipe; a transição confere o fluxo. As operações de atribuição — designar e limpar o responsável atual, mandar para o exclusivo guardando a origem, devolver, transferir — conferem o modo e nunca saem de um estado válido. `@Version` recusa a segunda de duas gravações simultâneas. O `toString` não leva texto livre. |
| `ticket/domain/TicketPriority.java`, `TicketCategory.java` | ✅ | Prioridade (chave da política de SLA) e categoria (chave do roteamento). Ficam em `domain` até outro módulo precisar delas. |
| `ticket/domain/Comment.java` | ✅ | Resposta pública ou nota interna, cada uma com a sua fábrica — um `boolean` trocado publicaria nota interna para o cliente. Imutável. |
| `ticket/domain/TicketRoute.java` | ✅ | Qual equipe recebe os tickets de uma categoria. Dado no banco, não regra em Java. |
| `ticket/domain/TicketRepository.java`, `CommentRepository.java`, `TicketRouteRepository.java` | ✅ | Portas de persistência. A de tickets é o ponto de troca de armazenamento, com contrato testado: listagem filtrada no armazenamento e ordenada por urgência, e a busca de sistema usada quando alguém sai da equipe. A de comentários tem uma consulta que já exclui nota interna no SQL. |
| `ticket/domain/VisibilityScope.java`, `TicketPage.java` | ✅ | O que alguém pode enxergar, na forma que a consulta precisa — admin tudo, solicitante só os seus, agente também os das suas equipes —, e uma página de tickets sem tipo do Spring Data. |
| `ticket/domain/InvalidStatusTransitionException.java`, `AssignmentConflictException.java` | ✅ | Transição fora do fluxo e atribuição incompatível com o modo do ticket (409). |
| `ticket/service/TicketAccessPolicy.java` | ✅ | Quem vê, atende e despacha um ticket **já carregado**: admin tudo; solicitante vê o seu e só fecha ou reabre; em modo equipe o membro atende e o líder também despacha; em modo exclusivo atuam o responsável e o líder da origem, e a equipe perde o acesso. É metade da regra de visibilidade — a outra é o `TicketSpecifications`, e as duas concordam caso a caso. |
| `ticket/service/TicketService.java` | ✅ | Abrir na equipe da rota, listar com o escopo montado do papel e das equipes lidas na hora, consultar, transicionar publicando `TicketStatusChanged` e conversar. Quem não vê recebe 404 igual a inexistente; quem vê sem poder, 403; fluxo inválido, 409. |
| `ticket/service/TicketRouteService.java` | ✅ | Cria ou redireciona a rota de uma categoria, recusando equipe inexistente. |
| `ticket/service/TicketAssignmentService.java` | ✅ | Responsável atual, exclusivo, devolução e transferência, sempre na ordem 404, 403, 409, 400 — o destino só é conferido depois, para quem não pode não descobrir quem é de qual equipe. Só mudança de verdade grava e publica `TicketAssignmentChanged`. Limpa o responsável de quem saiu da equipe. |
| `ticket/service/TeamMembershipListener.java` | ✅ | `@ApplicationModuleListener` de `TeamMembershipRemoved`: tira quem saiu da equipe de responsável atual dos tickets dela. |
| `ticket/service/*Exception.java` | ✅ | `TicketNotFound` (404), `TicketActionForbidden` (403), `UnroutedCategory` (409), `UnknownTeam` e `IneligibleAssignee` (400). |
| `ticket/infra/` | ✅ | Spring Data e os três adaptadores. A listagem monta a consulta em Criteria, porque a ordem por urgência não cabe num `Sort`. |
| `ticket/infra/TicketSpecifications.java` | ✅ | A regra de visibilidade em SQL: solicitante, responsável exclusivo, membro da equipe em modo equipe, líder da equipe de origem. O papel `REQUESTER` fica só com a primeira. Conjunto de equipes vazio não vira `IN ()`. |
| `ticket/web/v1/TicketController.java` + `dto/` | ✅ | `GET /api/v1/tickets` (paginado, 20 por padrão, no máximo 100), `POST /api/v1/tickets`, `GET /{id}`, `POST /{id}/transitions`, `GET` e `POST /{id}/comments`. A equipe não vem no pedido; `internal` é obrigatório no comentário, sem default. Sem regra no `SecurityConfig`. |
| `ticket/web/v1/TicketAssignmentController.java` | ✅ | `PUT` e `DELETE /api/v1/tickets/{id}/assignment/current`, e `POST .../exclusive`, `.../return` e `.../transfer`. Toda resposta devolve o ticket como ficou. |
| `ticket/web/v1/TicketRouteController.java` | ✅ | `GET /api/v1/ticket-routes` e `PUT /api/v1/ticket-routes/{category}`, só admin, por URL. |

### `src/main/resources/`

| Caminho | Status | O que faz |
|---|---|---|
| `application.yml` | ✅ | Datasource com defaults do Compose, `ddl-auto: validate`, Flyway, modo `archive` para eventos concluídos, reentrega no start de evento que um listener não concluiu, Jackson em UTC, virtual threads e probes do Actuator. Importa `backend/.env` se existir, define os placeholders do seed do admin, a configuração do login (`ticketsystem.auth.*`, sem default para o segredo) e tolera a ausência do seed repetível, para a mesma base subir com e sem o perfil `dev`. |
| `application-dev.yml` | ✅ | Acrescenta a location `db/seed` ao Flyway: é o único perfil que carrega a base de demonstração. `./mvnw spring-boot:run` ativa este perfil. |
| `db/migration/V1__create_event_publication.sql` | ✅ | Tabelas do registro de publicação de eventos do Modulith, ativa e de arquivo. É o que garante reprocessamento de listener que falhou. O cabeçalho registra a questão de retenção de dado pessoal no arquivo. |
| `db/migration/V2__create_users_and_teams.sql` | ✅ | Cria `users`, `teams` e `team_memberships`. Um usuário participa de várias equipes (UNIQUE no par); e-mail é guardado sempre em minúsculas, então o UNIQUE comum já resolve unicidade e login; nome de equipe é único por índice funcional, preservando a caixa digitada. Um CHECK exige BCrypt completo em `password_hash` — senha em texto, placeholder não substituído ou hash truncado não entram. |
| `db/migration/V3__seed_admin_user.sql` | ✅ | Semeia o primeiro administrador com o hash vindo de `ADMIN_PASSWORD_HASH`. Sem a variável, o CHECK da V2 derruba o boot nesta migration. |
| `db/migration/V4__create_refresh_tokens_and_login_lockouts.sql` | ✅ | Tabelas de sessão (`refresh_tokens`, só com hash SHA-256, família e motivo de revogação) e de tentativas de login (`login_lockouts`, uma linha por conta, fora de `users`). |
| `db/migration/V5__create_tickets_comments_and_routes.sql` | ✅ | `tickets`, `ticket_comments` e `ticket_routes`. CHECKs garantem exatamente um modo de atribuição, origem só em modo exclusivo e responsável atual só em modo equipe. Ticket segura pessoa e equipe por RESTRICT; comentário vai em cascata com o ticket; a flag `internal` é obrigatória, sem default. Índice por coluna que a visibilidade vai filtrar. |
| `db/seed/R__demo_users_and_teams.sql` | ✅ | Elenco de demonstração (duas equipes, líderes, agentes e solicitantes) e a rota de cada categoria, fora da linha de migrations versionadas. Repetível e idempotente; só roda no perfil `dev`. |

### `src/test/`

| Caminho | Status | O que faz |
|---|---|---|
| `ModularityTest.java` | ✅ | Roda `ApplicationModules.verify()` e gera os diagramas em `docs/modules/`. |
| `EventPublicationIT.java` | ✅ | Publica um evento numa transação e prova o caminho inteiro: gravado no registro, entregue ao listener assíncrono e movido para o arquivo ao concluir; e que a reentrega no start está ligada. |
| `TicketSystemApplicationIT.java` | ✅ | Sobe o contexto inteiro contra um Postgres real e confirma que Flyway e JPA ligaram. |
| `TicketsSchemaIT.java` | ✅ | Escrito antes da V5: os dois modos nunca juntos nem ausentes, origem e responsável atual só no modo certo, enums, títulos em branco, RESTRICT de pessoa e equipe, cascata e flag obrigatória do comentário, uma rota por categoria. |
| `UsersAndTeamsSchemaIT.java` | ✅ | Fixa as regras de `users`, `teams` e `team_memberships` que só existem quando o Postgres executa: unicidade e normalização de e-mail, papéis dentro do enum, participação em várias equipes, cascata dos vínculos, os formatos de hash aceitos e recusados, e que o admin semeado autentica com a senha que o README publica. |
| `config/JacksonConfigTest.java` | ✅ | Fixa a serialização de `Instant` como string ISO-8601 — o formato é contrato, não default de biblioteca. |
| `config/JpaConfigTest.java` | ✅ | O carimbo de auditoria em microssegundos, nem mais nem menos. |
| `common/domain/BaseEntityTest.java` | ✅ | A igualdade de entidade: duas instâncias sem id nunca são iguais, e o hash sobrevive à persistência. |
| `common/error/GlobalExceptionHandlerTest.java` | ✅ | Fixa o contrato de erro com MockMvc: `ProblemDetail` em validação, em método não suportado, em cada `ProblemKind`, em gravação concorrente (409) e em 401/403, sem vazar a mensagem interna. |
| `RefreshTokensAndLockoutsSchemaIT.java` | ✅ | Constraints da V4 contra Postgres real: hash de token só em SHA-256 hexadecimal, motivo de revogação coerente, um bloqueio por conta, cascata ao apagar o usuário. |
| `config/SecurityIT.java` | ✅ | A cadeia de filtros com tokens reais: expirado, outra chave, payload adulterado e `alg: none` dão 401; papel insuficiente dá 403; rota pública responde; tudo em `ProblemDetail`. |
| `config/OpenApiContractIT.java` | ✅ | O contrato publicado: os endpoints de sessão sem bearer, o resto com. |
| `user/domain/UserTest.java`, `user/service/UserServiceTest.java` | ✅ | Invariantes do usuário; conferência de senha, limite de 72 bytes e e-mail duplicado. |
| `user/web/v1/UserControllerIT.java` | ✅ | Criação por admin de ponta a ponta — inclusive o login de quem foi criado —, 409, 400, largura 150/151 e o caso negativo de permissão. |
| `auth/domain/*Test.java` | ✅ | Bordas do bloqueio e da validade do refresh token. |
| `auth/service/*Test.java` | ✅ | Claims do token (sem equipes), rotação e reuso, ordem do login, rejeições de configuração e `toString` que não vaza credencial. |
| `auth/infra/LoginLockoutRepositoryIT.java`, `RefreshTokenRepositoryIT.java` | ✅ | O SQL do contador e a revogação em massa contra Postgres real. |
| `auth/web/v1/AuthControllerIT.java` | ✅ | Login, bloqueio, rotação e reuso pelo endpoint, olhando o banco para provar que as proteções sobrevivem à exceção. |
| `auth/infra/JwtCurrentUserResolverTest.java` | ✅ | O ator lido do token, e cada jeito de a identidade ser duvidosa virar 401. |
| `team/domain/*Test.java`, `team/service/TeamServiceTest.java` | ✅ | Invariantes da equipe e do vínculo; cada regra de permissão, inclusive que a recusa acontece antes de consultar o alvo. |
| `team/infra/TeamRepositoryIT.java` | ✅ | Alguém em duas equipes com vínculos independentes, busca de nome sem caixa, ordem dos vínculos e larguras da V2. |
| `team/web/v1/TeamControllerIT.java` | ✅ | Os casos negativos pelo endpoint, olhando o banco depois da recusa: membro comum, líder de outra equipe e solicitante não gerenciam. Equipe invisível e inexistente dão o mesmo corpo; promoção e remoção valem na hora, com o mesmo token. |
| `ticket/TicketStatusTest.java` | ✅ | Os 36 pares de status, um a um, contra uma tabela escrita a partir do requisito. |
| `ticket/domain/*Test.java` | ✅ | Invariantes do ticket, do comentário e da rota, e `toString` sem texto livre. |
| `ticket/domain/TicketAssignmentTest.java` | ✅ | As cinco operações de atribuição e o invariante de modo que nenhuma pode quebrar. |
| `ticket/domain/TicketRepositoryContractTest.java` | ✅ | O contrato do repositório de tickets escrito contra a **interface**: recusa de cópia desatualizada, ordem por urgência, paginação, atribuição gravada e a busca de sistema por responsável atual. Abstrato: um adaptador novo o estende e roda os mesmos casos. |
| `ticket/service/*Test.java` | ✅ | A regra de acesso linha a linha, casos negativos primeiro; a ordem de recusas da atribuição e que pedido sem mudança não publica; e que o service consulta a regra antes de agir. |
| `ticket/infra/JpaTicketRepositoryIT.java`, `CommentRepositoryIT.java` | ✅ | O contrato contra o adaptador JPA em Postgres real, e a conversa pública sem nota interna no SQL. |
| `ticket/infra/TicketSpecificationsIT.java` | ✅ | Escrito antes do filtro, e visto falhar sem ele: um caso por linha da tabela de visibilidade, cada um com o conjunto exato, mais a comparação pessoa por pessoa e ticket por ticket entre a listagem e o `TicketAccessPolicy`. |
| `ticket/web/v1/TicketControllerIT.java` | ✅ | Pelo endpoint, olhando o banco depois de cada recusa: outro solicitante e agente de outra equipe recebem 404 igual a inexistente; solicitante não conduz o atendimento nem escreve nota interna, e nunca recebe uma; sair da equipe vale na hora. |
| `ticket/web/v1/TicketAssignmentControllerIT.java` | ✅ | Cada movimento seguido de "quem vê agora": o exclusivo tira a equipe e deixa responsável e líder, a transferência tira a equipe antiga, a devolução a traz de volta; recusas 403, 404 e 400 olhando o banco; e a saída da equipe limpando o responsável pelo listener. |
| `ticket/web/v1/TicketRouteControllerIT.java` | ✅ | Roteamento só por admin, redirecionamento sem duplicar, equipe e categoria inexistentes. |
| `support/UserBuilder.java` | ✅ | Usuário de teste em uma linha, com e-mail único e id sintético — sem o id, `BaseEntity.equals` faria teste de visibilidade passar por acidente. Não conhece equipe. |
| `support/TeamBuilder.java` | ✅ | Equipe de teste com os vínculos em uma linha, recebendo ids de usuário e não a entidade `User`. Monta alguém em duas equipes, o caso que a Fase 5 exercita. |
| `support/TicketBuilder.java` | ✅ | Ticket de teste em uma linha, por ids. Põe o ticket em qualquer status ou em modo exclusivo direto, sem percorrer o fluxo. |
| `support/AuthTokens.java` | ✅ | Token válido e cada forja de token inválido num lugar só, assinando com o encoder da própria aplicação. |
| `support/SecureMockMvc.java` | ✅ | MockMvc sobre o contexto com a cadeia de segurança real, sem precisar do `spring-boot-webmvc-test`. |
| `support/PostgresContainer.java` | ✅ | Container Postgres 16 reaproveitado, ligado ao contexto por `@ServiceConnection`. |
| `support/IntegrationTest.java` | ✅ | Anotação-base que junta `@SpringBootTest`, perfil de teste e o container, e anula o import do `.env` para a suíte não enxergar a configuração da máquina de quem roda. |
| `resources/application-test.yml` | ✅ | Configuração dos testes. Sem datasource fixo: a URL vem do container. Fixa os placeholders do seed do admin e a configuração do login, para a suíte não depender de um `.env` na máquina, e deixa a location `db/seed` de fora. |

## Frontend — `frontend/`

Organização por módulo, atomic design em quatro níveis dentro de cada um.

| Caminho | Status | O que faz |
|---|---|---|
| `package.json` | ✅ | Next 16, React 19, TypeScript 5.9, Vitest, MSW, `openapi-typescript`. Scripts `dev`, `build`, `lint`, `typecheck`, `test`, `gen:api`. |
| `next.config.mjs` | ✅ | `output: 'standalone'` para a imagem de produção, strict mode e rotas tipadas. |
| `tsconfig.json` | ✅ | Strict com `noUncheckedIndexedAccess` e alias `@/*`. |
| `eslint.config.mjs` | ✅ | Flat config nativo do `eslint-config-next` 16, sem `FlatCompat`. |
| `vitest.config.mts` | ✅ | jsdom, alias `@/*` e CSS ligado. É `.mts` porque `.ts` seria carregado como CommonJS. |
| `vitest.setup.ts` | ✅ | Carrega os matchers do `jest-dom`. |
| `.env.example`, `.nvmrc` | ✅ | Modelo de variáveis e a versão do Node (24). |

### `src/`

| Caminho | Status | O que faz |
|---|---|---|
| `app/layout.tsx` | ✅ | Layout raiz: `metadata`, idioma e estilos globais. |
| `app/page.tsx`, `app/page.module.css` | ✅ | Placeholder da raiz. Sai quando a rota de tickets existir. |
| `styles/tokens.css` | ✅ | Design tokens: cor (incluindo um por status e por prioridade), espaço, tipografia, raio e sombra, com tema escuro. Todo `styles.module.css` consome daqui. |
| `styles/globals.css` | ✅ | Reset, estilos de documento e respeito a `prefers-reduced-motion`. |
| `lib/http/client.ts` | ✅ | `fetch` tipado do lado servidor, com `server-only` para o build quebrar se um Client Component importar. |
| `modules/shared/components/atoms/Button/` | ✅ | Primeiro atom, com teste. Serve de modelo da anatomia de componente: arquivo, teste, `styles.module.css` e `index.ts`. |
| `modules/{ticket,team,user,auth}/` | — | **Não existem ainda.** Cada pasta nasce com o primeiro arquivo dela, na forma descrita na Parte 2 — não há diretório vazio reservando lugar. |
| `types/api.d.ts` | ✅ | Tipos gerados do schema OpenAPI por `pnpm gen:api`. Hoje vazio porque não há endpoint; o pipeline foi validado de ponta a ponta. **Não edite à mão.** |

## E2E — `e2e/`

| Caminho | Status | O que faz |
|---|---|---|
| `package.json` | ✅ | Playwright 1.63 e os scripts da suíte. |
| `playwright.config.ts` | ✅ | Aponta para a stack local, com trace, screenshot e vídeo retidos em falha. |
| `specs/smoke.spec.ts` | ✅ | Confere que o frontend responde e que o backend está `UP`. Prova o harness; os fluxos reais vêm com as features. |

## Infraestrutura — `docker/`

| Caminho | Status | O que faz |
|---|---|---|
| `docker-compose.yml` | ✅ | Postgres, backend e frontend, com healthcheck e `depends_on` encadeado. `up -d` sobe tudo; `up -d postgres` sobe só o banco. |
| `backend.Dockerfile` | 🚧 | Multi-stage Temurin 25: baixa dependências antes de copiar o código, e o runtime é JRE com usuário sem privilégio. **Nunca foi construído com sucesso** — ver abaixo. |
| `frontend.Dockerfile` | 🚧 | Três estágios sobre Node 24, publicando a saída `standalone` do Next. **Idem.** |

> ⚠️ **`docker compose build` não foi validado.** Na máquina de desenvolvimento o Avast
> intercepta TLS, e o truststore dentro do container não conhece a CA dele: o download
> do Maven Central e do registry do npm falha com `certificate verify failed`. O
> `docker compose up -d postgres` funciona (só puxa imagem pronta), e backend e frontend
> foram validados rodando na máquina. **Os dois Dockerfiles ainda podem ter erros.**
> Para construí-los é preciso desligar a inspeção HTTPS do antivírus ou usar uma rede
> sem interceptação.

## Kubernetes — `k8s/`

Demonstração de deploy sobre as **mesmas imagens** que o Compose constrói. Não é o
ambiente de desenvolvimento — ver
[ADR 0002](docs/adr/0002-infra-local-compose-e-kubernetes.md). A regra de sincronia com
o Compose está em [CLAUDE.md](CLAUDE.md#kubernetes-em-k8s).

| Caminho | Status | O que faz |
|---|---|---|
| `kind-config.yaml` | ✅ | Cluster de um nó com as portas 80 e 443 mapeadas e o nó marcado para o ingress. |
| `base/kustomization.yaml` | ✅ | Junta os manifests no namespace `ticket-system`. |
| `base/namespace.yaml` | ✅ | O namespace. |
| `base/config.yaml` | ✅ | ConfigMap com a configuração e Secret com credenciais de desenvolvimento. |
| `base/postgres.yaml` | ✅ | PVC, Deployment com estratégia `Recreate` e Service do banco. |
| `base/backend.yaml` | ✅ | Deployment com startup, readiness e liveness nas sondas do Actuator, mais o Service. |
| `base/frontend.yaml` | ✅ | Deployment e Service do Next. |
| `base/ingress.yaml` | ✅ | Roteia `/` para o frontend e `/api`, `/swagger-ui` e `/v3/api-docs` para o backend. |

## Documentação — `docs/`

| Caminho | Status | O que faz |
|---|---|---|
| `adr/0001-versoes-da-stack.md` | ✅ | As versões escolhidas, as quatro armadilhas confirmadas na prática e por que TypeScript e ESLint ficam atrás do `latest`. |
| `adr/0002-infra-local-compose-e-kubernetes.md` | ✅ | Por que Compose desenvolve e Kubernetes demonstra, e o que mantém o `k8s/` honesto. |
| `adr/0003-autenticacao-jwt.md` | ✅ | Por que o token carrega só `sub` e `role`, HS256 em vez de RS256, refresh opaco em banco com rotação e bloqueio por conta — e os custos aceitos de cada escolha. |
| `roadmap.md` | ✅ | O que falta para a aplicação completa, em fases ordenadas por dependência, com as premissas de produto assumidas e os riscos conhecidos. Item concluído sai dali e entra aqui. |
| `modules/` | ✅ | Diagramas PlantUML e canvas por módulo. **Saída de build**: regerados a cada `./mvnw test`, nunca escritos à mão. |

---

# Parte 2 — Estrutura planejada

> ⚠️ **Nada abaixo desta linha existe no repositório.** São caminhos e
> responsabilidades acordados, para orientar quem for implementar e manter o vocabulário
> consistente. Ao criar um arquivo de verdade, mova a linha dele para a Parte 1.

## Índice rápido: onde vai ficar...

Esta tabela é a exceção à separação das duas partes: ela aponta para onde cada assunto
mora, exista o arquivo ou não. O ✅ marca o caminho que **já está no disco** — os demais
ainda são planejados.

| Quero... | Vai estar em |
|---|---|
| Mudar quem pode ver um ticket | `backend/.../ticket/service/TicketAccessPolicy.java` ✅ + `ticket/infra/TicketSpecifications.java` ✅ — as duas juntas, sempre, e o `TicketSpecificationsIT` confere |
| Mudar as transições de status permitidas | `backend/.../ticket/TicketStatus.java` ✅ |
| Mudar para qual equipe vai uma categoria | `PUT /api/v1/ticket-routes/{category}`, como admin; em dev, `db/seed/` ✅ |
| Mudar como o prazo de SLA é calculado | `backend/.../sla/service/SlaClock.java` |
| Chamar um módulo a partir de outro | A fachada na raiz do módulo alvo (`ticket/TicketFacade.java`) — nunca uma classe interna |
| Reagir a algo que aconteceu em outro módulo | Um `@ApplicationModuleListener` no seu próprio módulo |
| Alterar o schema do banco | Nova migration em `backend/src/main/resources/db/migration/` ✅ |
| Mudar o elenco de demonstração | `backend/src/main/resources/db/seed/` ✅ — só carrega no perfil `dev` |
| Adicionar um endpoint | `backend/.../<feature>/web/v1/` e depois regenerar os tipos do frontend |
| Trocar o armazenamento de um módulo | Novo adaptador em `backend/.../<feature>/infra/`; a interface fica em `domain/` |
| Mudar login / emissão de token | `backend/.../auth/` ✅ e `frontend/src/lib/auth/` |
| Mudar quem gerencia uma equipe | `backend/.../team/service/TeamService.java` ✅ |
| Mudar quem pode chamar qual rota | `backend/.../config/SecurityConfig.java` ✅ |
| Mudar uma tela | `frontend/src/modules/{módulo}/components/pages/` (composição) e `frontend/src/app/` (auth e dados) |
| Criar um componente novo | `frontend/src/modules/{módulo}/components/{atoms\|molecules\|organisms}/` |
| Mudar como o frontend lê da API | `frontend/src/modules/{módulo}/services/` e `frontend/src/lib/http/client.ts` ✅ |
| Mudar como o frontend escreve na API | `frontend/src/modules/{módulo}/actions/` |
| Mudar cor, espaçamento ou raio | `frontend/src/styles/tokens.css` ✅ |
| Mudar variáveis de ambiente ou containers | `docker/` ✅, os `.env.example` ✅ e, na mesma alteração, `k8s/base/` ✅ |
| Entender o que testar antes e o que testar depois | [CLAUDE.md](CLAUDE.md#testes) |
| Escrever um teste de integração | `backend/src/test/java/com/ticketsystem/support/` ✅ — `IntegrationTest`, `UserBuilder`, `TeamBuilder`, `TicketBuilder`, `AuthTokens`, `SecureMockMvc` |
| Escrever um teste E2E | `e2e/specs/` ✅ |

---

## Backend — o que falta

O porquê de cada decisão está em [CLAUDE.md](CLAUDE.md#arquitetura-e-versionamento).
Dentro de cada feature, só `web` é versionado — `domain`, `service` e `infra` são únicos.

### `user/` — usuários

| Caminho | O que fará |
|---|---|
| `user/domain/WorkSchedule.java` | Horário de trabalho customizado (faixas por dia da semana + zona). Opcional. Entra com o relógio de SLA, na Fase 6. |
| `GET /api/v1/users` | Listagem de usuários para admin, no formato `PageResponse`. |

### `team/` — equipes

| Caminho | O que fará |
|---|---|
| `GET /api/v1/teams` | Listagem de equipes, no formato `PageResponse`. |

### `ticket/` — o núcleo do domínio

| Caminho | O que fará |
|---|---|
| `ticket/TicketFacade.java` | API pública do módulo, com o primeiro módulo que precisar perguntar algo sobre tickets. |

### `sla/` — prazos

| Caminho | O que fará |
|---|---|
| `sla/SlaFacade.java` | API pública: devolve os prazos de um ticket para quem precisar exibi-los. |
| `sla/domain/SlaPolicy.java` | Prazo de primeira resposta e de resolução para uma prioridade. |
| `sla/domain/BusinessCalendar.java` | Horário comercial global usado quando o responsável não tem horário próprio. |
| `sla/service/SlaClock.java` | Converte duração de SLA em prazo real, pulando fora de expediente e o tempo em `WAITING_CUSTOMER`. |
| `sla/service/SlaService.java` | Calcula e recalcula os prazos de um ticket. |
| `sla/service/TicketEventListener.java` | `@ApplicationModuleListener` de `TicketStatusChanged`: dispara o recálculo. É o que mantém `ticket` sem saber que SLA existe. |

### `audit/` — histórico

| Caminho | O que fará |
|---|---|
| `audit/AuditFacade.java` | API pública: devolve a linha do tempo de um ticket. |
| `audit/domain/AuditEvent.java` | Registro append-only de transição e reatribuição. |
| `audit/domain/AuditEventRepository.java` | Interface de persistência do histórico. |
| `audit/infra/JpaAuditEventRepository.java` | Implementação sobre Spring Data. |
| `audit/service/TicketEventListener.java` | `@ApplicationModuleListener` que transforma evento de ticket em registro de auditoria. |

### `db/migration/` — schema

| Caminho | O que fará |
|---|---|
| `V6__create_audit_events.sql` | Eventos de auditoria, append-only. Os comentários, previstos aqui, entraram na `V5`. |
| `V7__create_sla.sql` | Políticas de SLA, calendário comercial e horários customizados. |

A numeração vai até a `V5`, na Parte 1. Migration é forward-only: o que vier entra na `V6`
em diante, nunca editando as anteriores.

Nomes sujeitos a ajuste conforme a implementação avança.

### Testes que faltam

A estrutura espelha a de `main/`. A estratégia está em [CLAUDE.md](CLAUDE.md#testes).

| Caminho | O que fará |
|---|---|
| `sla/service/SlaClockTest.java` | Casos de borda do relógio: virada de expediente, fim de semana e tempo parado em `WAITING_CUSTOMER`. |

---

## Frontend — o que falta

### `src/app/` — rotas

Cada `page.tsx` resolve autenticação, busca os dados e define `metadata`, depois entrega
a composição ao componente de página do módulo.

| Caminho | O que fará |
|---|---|
| `app/(auth)/login/page.tsx` | Rota de login. |
| `app/(app)/tickets/page.tsx` | Carrega os tickets visíveis ao usuário e delega para `TicketListPage`. |
| `app/(app)/tickets/[id]/page.tsx` | Carrega o ticket e delega para `TicketDetailPage`. |
| `app/(app)/tickets/new/page.tsx` | Rota de abertura de chamado. |
| `app/(app)/teams/page.tsx` | Rota de gestão de equipes e membros. |
| `app/api/auth/[...]/route.ts` | Route Handlers que fazem login e guardam o JWT em cookie `httpOnly`. |

### `src/modules/ticket/`

| Caminho | O que fará |
|---|---|
| `components/pages/TicketDetailPage/` | Tela de detalhe. Compõe os organisms e recebe tudo por props — não busca dado nenhum. |
| `components/pages/TicketListPage/` | Tela de listagem com filtros. |
| `components/organisms/TicketTable/` | Tabela de tickets com indicação visual de SLA. |
| `components/organisms/CommentThread/` | Lista de comentários, escondendo os internos do solicitante. |
| `components/organisms/AssignmentPanel/` | Ações de atribuição, respeitando o que o usuário atual pode fazer. |
| `components/molecules/SlaIndicator/` | Tempo restante do prazo, destacando o que estourou. |
| `components/atoms/StatusBadge/` | Traduz status em rótulo e cor (tokens `--color-status-*`). |
| `components/atoms/PriorityTag/` | Traduz prioridade em rótulo e cor (tokens `--color-priority-*`). |
| `actions/` | Server Actions de escrita: abrir, transicionar, comentar e atribuir. |
| `services/` | Leitura da API de tickets, chamada a partir do servidor. |
| `types/` | Tipos de UI do módulo. Os de contrato vêm de `src/types/api.d.ts`. |

`modules/team/`, `modules/user/` e `modules/auth/` seguem a mesma forma interna.

### `src/modules/shared/` e `src/lib/`

| Caminho | O que fará |
|---|---|
| `shared/components/atoms/` | Além do `Button`: `Input`, `Select`. |
| `shared/components/molecules/` | `Modal`, `Pagination`, `EmptyState`. |
| `shared/hooks/`, `shared/utils/` | O que atravessa módulos: formatação de data, texto e número. |
| `lib/auth/session.ts` | Lê e valida a sessão a partir do cookie, para uso em Server Components. |
| `lib/permissions.ts` | Espelha as regras de visibilidade **apenas** para mostrar ou esconder botões. A decisão real é sempre do backend. |

### E2E

| Caminho | O que fará |
|---|---|
| `e2e/specs/` | Um arquivo por fluxo de usuário (abrir chamado, atender, atribuir, resolver). |
| `e2e/fixtures/` | Sessões pré-autenticadas por papel, para não repetir login em cada teste. |
| `e2e/seed/` | Carga inicial de dados usada pelos cenários. |

### Documentação

| Caminho | O que fará |
|---|---|
| `docs/domain-model.md` | Diagrama de entidades e detalhamento das regras de atribuição. |
