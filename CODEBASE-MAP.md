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

**Estado do repositório: esqueleto de pé, sem domínio.** Backend compila, sobe e passa
`./mvnw verify`; frontend passa `lint`, `typecheck`, `test` e `build`; a stack tem
Compose e manifests de Kubernetes. **Nenhuma regra de negócio foi implementada** — não
há entidade, migration, endpoint nem tela de ticket.

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
| `pom.xml` | ✅ | Boot 4.1.1 sobre Java 25, Modulith com registro de eventos em JPA, springdoc, Flyway, Testcontainers, JaCoCo com threshold em domain/service e PITest. Os comentários dele registram as armadilhas de versão. |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/` | ✅ | Wrapper do Maven 3.9.12: o build funciona sem Maven instalado. |
| `.env.example` | ✅ | Modelo das variáveis. Opcional para desenvolver — `application.yml` já tem defaults que apontam para o Postgres do Compose. |

### `src/main/java/com/ticketsystem/`

| Caminho | Status | O que faz |
|---|---|---|
| `TicketSystemApplication.java` | ✅ | Entrypoint do Spring Boot. |
| `config/package-info.java` | ✅ | Declara `config` como módulo aberto do Modulith. |
| `config/SecurityConfig.java` | ✅ | Cadeia de filtros stateless: libera health e a documentação do contrato, exige autenticação no resto. Sem mecanismo de login ainda — nega por padrão. |
| `config/JacksonConfig.java` | ✅ | Força data em ISO-8601. Existe como classe porque no Jackson 3 a flag mudou de enum e o Boot 4 não expõe propriedade para ela. |
| `config/OpenApiConfig.java` | ✅ | Metadados e esquema de segurança do schema em `/v3/api-docs`, de onde saem os tipos do frontend. |
| `config/JpaConfig.java` | ✅ | Liga a auditoria do Spring Data que preenche `createdAt` e `updatedAt`. |
| `common/package-info.java` | ✅ | Declara `common` como módulo aberto do Modulith. |
| `common/error/FieldProblem.java` | ✅ | Um campo rejeitado pela validação e o motivo. Vai na propriedade `errors` do `ProblemDetail`. |
| `common/error/GlobalExceptionHandler.java` | ✅ | Traduz exceção em resposta HTTP no formato `ProblemDetail` (RFC 9457). Herda de `ResponseEntityExceptionHandler`, então 404, 405 e 415 já saem padronizados; cada módulo registra aqui as suas exceções de domínio. |
| `common/domain/BaseEntity.java` | ✅ | Id e carimbos de tempo em UTC, com igualdade por id. |
| `auth/`, `user/`, `team/`, `ticket/`, `sla/`, `audit/` | 🚧 | Só `package-info.java` com javadoc descrevendo o módulo. **A anotação `@ApplicationModule` entra junto com a primeira classe** — anotar pacote vazio quebra o build. |

### `src/main/resources/`

| Caminho | Status | O que faz |
|---|---|---|
| `application.yml` | ✅ | Datasource com defaults do Compose, `ddl-auto: validate`, Flyway, modo `archive` para eventos concluídos, Jackson em UTC, virtual threads e probes do Actuator. |
| `db/migration/V1__create_event_publication.sql` | ✅ | Tabelas do registro de publicação de eventos do Modulith, ativa e de arquivo. É o que garante reprocessamento de listener que falhou. O cabeçalho registra a questão de retenção de dado pessoal no arquivo. |

### `src/test/`

| Caminho | Status | O que faz |
|---|---|---|
| `ModularityTest.java` | ✅ | Roda `ApplicationModules.verify()` e gera os diagramas em `docs/modules/`. |
| `EventPublicationIT.java` | ✅ | Publica um evento numa transação e prova o caminho inteiro: gravado no registro, entregue ao listener assíncrono e movido para o arquivo ao concluir. |
| `TicketSystemApplicationIT.java` | ✅ | Sobe o contexto inteiro contra um Postgres real e confirma que Flyway e JPA ligaram. |
| `config/JacksonConfigTest.java` | ✅ | Fixa a serialização de `Instant` como string ISO-8601 — o formato é contrato, não default de biblioteca. |
| `common/domain/BaseEntityTest.java` | ✅ | A igualdade de entidade: duas instâncias sem id nunca são iguais, e o hash sobrevive à persistência. |
| `common/error/GlobalExceptionHandlerTest.java` | ✅ | Fixa o contrato de erro com MockMvc: `ProblemDetail` em validação e em método não suportado, com os campos rejeitados ordenados. |
| `support/PostgresContainer.java` | ✅ | Container Postgres 16 reaproveitado, ligado ao contexto por `@ServiceConnection`. |
| `support/IntegrationTest.java` | ✅ | Anotação-base que junta `@SpringBootTest`, perfil de teste e o container. |
| `resources/application-test.yml` | ✅ | Configuração dos testes. Sem datasource fixo: a URL vem do container. |

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
| `roadmap.md` | ✅ | O que falta para a aplicação completa, em fases ordenadas por dependência, com as premissas de produto assumidas e os riscos conhecidos. Item concluído sai dali e entra aqui. |
| `modules/` | ✅ | Diagramas PlantUML e canvas por módulo. **Saída de build**: regerados a cada `./mvnw test`, nunca escritos à mão. |

---

# Parte 2 — Estrutura planejada

> ⚠️ **Nada abaixo desta linha existe no repositório.** São caminhos e
> responsabilidades acordados, para orientar quem for implementar e manter o vocabulário
> consistente. Ao criar um arquivo de verdade, mova a linha dele para a Parte 1.

## Índice rápido: onde vai ficar...

| Quero... | Vai estar em |
|---|---|
| Mudar quem pode ver um ticket | `backend/.../ticket/service/TicketAccessPolicy.java` + `ticket/infra/TicketSpecifications.java` |
| Mudar as transições de status permitidas | `backend/.../ticket/domain/TicketStatus.java` |
| Mudar como o prazo de SLA é calculado | `backend/.../sla/service/SlaClock.java` |
| Chamar um módulo a partir de outro | A fachada na raiz do módulo alvo (`ticket/TicketFacade.java`) — nunca uma classe interna |
| Reagir a algo que aconteceu em outro módulo | Um `@ApplicationModuleListener` no seu próprio módulo |
| Alterar o schema do banco | Nova migration em `backend/src/main/resources/db/migration/` |
| Adicionar um endpoint | `backend/.../<feature>/web/v1/` e depois regenerar os tipos do frontend |
| Trocar o armazenamento de um módulo | Novo adaptador em `backend/.../<feature>/infra/`; a interface fica em `domain/` |
| Mudar login / emissão de token | `backend/.../auth/` e `frontend/src/lib/auth/` |
| Mudar uma tela | `frontend/src/modules/{módulo}/components/pages/` (composição) e `frontend/src/app/` (auth e dados) |
| Criar um componente novo | `frontend/src/modules/{módulo}/components/{atoms\|molecules\|organisms}/` |
| Mudar como o frontend lê da API | `frontend/src/modules/{módulo}/services/` e `frontend/src/lib/http/client.ts` ✅ |
| Mudar como o frontend escreve na API | `frontend/src/modules/{módulo}/actions/` |
| Mudar cor, espaçamento ou raio | `frontend/src/styles/tokens.css` ✅ |
| Mudar variáveis de ambiente ou containers | `docker/` ✅, os `.env.example` ✅ e, na mesma alteração, `k8s/base/` ✅ |
| Entender o que testar antes e o que testar depois | [CLAUDE.md](CLAUDE.md#testes) |
| Escrever um teste de integração | `backend/src/test/java/com/ticketsystem/support/` ✅ (builders ainda não existem) |
| Escrever um teste E2E | `e2e/specs/` ✅ |

---

## Backend — o que falta

O porquê de cada decisão está em [CLAUDE.md](CLAUDE.md#arquitetura-e-versionamento).
Dentro de cada feature, só `web` é versionado — `domain`, `service` e `infra` são únicos.

### `auth/` — autenticação

| Caminho | O que fará |
|---|---|
| `auth/AuthFacade.java` | API pública: resolve o usuário autenticado para os demais módulos. |
| `auth/web/v1/AuthController.java` | Endpoints de login e refresh de token. |
| `auth/service/JwtService.java` | Emite e valida os tokens assinados. A biblioteca ainda não foi escolhida — ver a nota sobre Jackson 3 no [ADR 0001](docs/adr/0001-versoes-da-stack.md). |
| `auth/service/AuthService.java` | Confere credenciais e monta o token com papel e equipes do usuário. |
| `config/JwtAuthenticationFilter.java` | Lê o token de cada requisição, valida e popula o `SecurityContext`. Entra na cadeia do `SecurityConfig`, que hoje nega tudo que não é público. |

### `user/` — usuários

| Caminho | O que fará |
|---|---|
| `user/UserFacade.java` | API pública: consulta de usuário por id, para os demais módulos. |
| `user/domain/User.java` | Entidade do usuário: credenciais, nome, papel global e horário de trabalho opcional. |
| `user/domain/UserRole.java` | Enum `REQUESTER`, `AGENT`, `ADMIN`. |
| `user/domain/WorkSchedule.java` | Horário de trabalho customizado (faixas por dia da semana + zona). Opcional. |
| `user/domain/UserRepository.java` | Interface do repositório de usuários, em linguagem de negócio. |
| `user/infra/JpaUserRepository.java` | Implementa a interface acima sobre Spring Data. |
| `user/web/v1/UserController.java` | CRUD de usuários, restrito a admin. |

### `team/` — equipes

| Caminho | O que fará |
|---|---|
| `team/TeamFacade.java` | API pública: responde "este usuário é membro desta equipe?" e "quem lidera esta equipe?" — é o que `ticket` consome para decidir visibilidade. |
| `team/domain/Team.java` | Entidade da equipe. |
| `team/domain/TeamMembership.java` | Vincula usuário e equipe com papel `MEMBER` ou `LEAD`. |
| `team/domain/TeamMembershipRepository.java` | Interface de consulta de vínculos. |
| `team/infra/JpaTeamMembershipRepository.java` | Implementa a interface acima sobre Spring Data. |
| `team/service/TeamService.java` | Criação de equipes e gestão de membros. |
| `team/web/v1/TeamController.java` | Endpoints de equipe. |

### `ticket/` — o núcleo do domínio

| Caminho | O que fará |
|---|---|
| `ticket/TicketFacade.java` | API pública do módulo. Único ponto por onde outro módulo fala com tickets. |
| `ticket/TicketStatusChanged.java` | Evento publicado a cada transição. Consumido por `sla` e `audit`. |
| `ticket/TicketAssignmentChanged.java` | Evento publicado a cada reatribuição. |
| `ticket/domain/Ticket.java` | Entidade do chamado. Guarda os dois modos de atribuição e garante que só um esteja ativo. |
| `ticket/domain/TicketStatus.java` | Enum dos status **e** as transições permitidas entre eles. |
| `ticket/domain/TicketPriority.java` | Enum de prioridade; é a chave de busca da política de SLA. |
| `ticket/domain/Comment.java` | Comentário do ticket, com flag de interno/público. |
| `ticket/domain/TicketRepository.java` | **Interface** do repositório, em linguagem de negócio. É o ponto de troca de armazenamento. |
| `ticket/service/TicketService.java` | Orquestra abertura, transição de status e comentários, e publica os eventos. |
| `ticket/service/TicketAssignmentService.java` | Troca entre modo equipe e modo exclusivo e valida quem pode fazer isso. |
| `ticket/service/TicketAccessPolicy.java` | Responde "este usuário pode ver/atuar neste ticket?". Fonte única da regra de visibilidade. |
| `ticket/infra/SpringDataTicketRepository.java` | Interface Spring Data com `JpaSpecificationExecutor`; detalhe de implementação, o service não a enxerga. |
| `ticket/infra/JpaTicketRepository.java` | Adapta a interface Spring Data para a interface de domínio. |
| `ticket/infra/TicketSpecifications.java` | Traduz a regra de visibilidade em predicados SQL, para filtrar no banco e não em memória. |
| `ticket/web/v1/TicketController.java` | Endpoints de ticket da v1. |
| `ticket/web/v1/dto/` | Records de entrada e saída da API de tickets na v1. |

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
| `V2__create_users_and_teams.sql` | Tabelas de usuário, equipe e vínculo. |
| `V3__create_tickets.sql` | Tabela de tickets, com a constraint que impede os dois modos de atribuição ao mesmo tempo. |
| `V4__create_comments_and_audit.sql` | Comentários e eventos de auditoria. |
| `V5__create_sla.sql` | Políticas de SLA, calendário comercial e horários customizados. |

A `V1` já existe e criou o registro de eventos do Modulith — migration é forward-only,
então a numeração do domínio começa na `V2`.

Nomes sujeitos a ajuste conforme a implementação avança.

### Testes que faltam

A estrutura espelha a de `main/`. A estratégia está em [CLAUDE.md](CLAUDE.md#testes).

| Caminho | O que fará |
|---|---|
| `support/TicketBuilder.java` | Monta tickets para teste em uma linha, escondendo o setup de equipe, solicitante e SLA. |
| `support/UserBuilder.java`, `support/TeamBuilder.java` | Mesmo papel para usuários, equipes e vínculos. |
| `ticket/service/` | Testes unitários das regras de transição, atribuição e acesso. |
| `ticket/domain/TicketRepositoryContractTest.java` | Testes de contrato escritos contra a **interface** do repositório. Qualquer adaptador futuro roda esta mesma suíte sem reescrita. |
| `ticket/infra/TicketSpecificationsIT.java` | **Escrito antes da implementação.** Verifica contra Postgres real que a listagem não devolve ticket que o usuário não pode ver. |
| `ticket/web/v1/` | Testes de controller com MockMvc: status HTTP, formato de erro e serialização. |
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
