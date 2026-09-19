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

**Estado do repositório: apenas documentação.** Não há código, build, configuração nem
dependências. Se você está procurando implementação, ela não está em lugar nenhum — o
que existe sobre ela é desenho, na Parte 2.

| Caminho | Status | O que é |
|---|---|---|
| `README.md` | ✅ | Visão geral do produto, modelo de domínio e como rodar. |
| `CLAUDE.md` | ✅ | Regras de trabalho, convenções e regras de domínio invioláveis. |
| `CODEBASE-MAP.md` | ✅ | Este arquivo. |
| `LICENSE` | ✅ | Licença do projeto. |
| `.gitignore` | ✅ | Ignora artefatos de Node, Java/Maven, IDEs e do sistema operacional. Versiona de propósito o wrapper do Maven, os `.env.example` e os arquivos compartilhados do VS Code. |

**Não existem ainda:** `backend/`, `frontend/`, `e2e/`, `docker/`, `docs/`.

**Legenda:** ✅ pronto · 🚧 em andamento. Só aparecem na Parte 1 — na Parte 2 tudo é
planejado por definição, e marcar isso linha a linha seria ruído.

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
| Mudar como o frontend lê da API | `frontend/src/modules/{módulo}/services/` e `frontend/src/lib/http/client.ts` |
| Mudar como o frontend escreve na API | `frontend/src/modules/{módulo}/actions/` |
| Mudar cor, espaçamento ou raio | `frontend/src/styles/tokens.css` |
| Mudar variáveis de ambiente ou containers | `docker/` e os `.env.example` |
| Entender o que testar antes e o que testar depois | [CLAUDE.md](CLAUDE.md#testes) |
| Escrever um teste de integração | `backend/src/test/java/com/ticketsystem/support/` (builders e classe-base) |
| Escrever um teste E2E | `e2e/specs/` |

## Raiz do repositório

| Caminho | O que será |
|---|---|
| `backend/` | API REST em Spring Boot. |
| `frontend/` | Aplicação Next.js. |
| `docker/` | Compose e infraestrutura local. |
| `docs/` | Decisões de arquitetura (ADRs) e diagramas. |
| `e2e/` | Suíte Playwright, que roda contra a stack completa no Docker. Fica na raiz porque atravessa frontend e backend. |

---

## Backend — `backend/`

Estrutura Maven padrão. Pacote raiz: `com.ticketsystem`. A organização é **por feature**,
não por camada técnica, e as fronteiras são verificadas por **Spring Modulith**: o que
está na raiz do pacote do módulo é público, o que está em subpacote é interno. Dentro
de cada feature, só `web` é versionado — `domain`, `service` e `infra` são únicos.

O porquê de cada decisão está em
[CLAUDE.md](CLAUDE.md#arquitetura-e-versionamento).

### Estrutura geral

```
backend/
├── pom.xml                                  dependências e plugins Maven
├── mvnw, mvnw.cmd                           wrapper do Maven
├── .env.example                             modelo das variáveis de ambiente
└── src/
    ├── main/
    │   ├── java/com/ticketsystem/
    │   │   ├── TicketSystemApplication.java  entrypoint Spring Boot
    │   │   ├── config/                       configuração transversal (módulo aberto)
    │   │   ├── common/                       código compartilhado (módulo aberto)
    │   │   ├── auth/                         autenticação e emissão de JWT
    │   │   ├── user/                         usuários e papéis globais
    │   │   ├── team/                         equipes e vínculos de membro
    │   │   ├── ticket/                       o núcleo do domínio
    │   │   │   ├── TicketFacade.java          API pública do módulo
    │   │   │   ├── TicketStatusChanged.java   evento público
    │   │   │   ├── domain/                    entidades, regras, interface do repositório
    │   │   │   ├── service/                   casos de uso
    │   │   │   ├── infra/                     implementação do repositório
    │   │   │   └── web/v1/                    controller + DTOs da v1
    │   │   ├── sla/                          políticas e relógio de SLA
    │   │   └── audit/                        histórico imutável de mudanças
    │   └── resources/
    │       ├── application.yml               configuração da aplicação
    │       └── db/migration/                 migrations Flyway
    └── test/
        ├── java/com/ticketsystem/
        │   ├── ModularityTest.java           verifica as fronteiras entre módulos
        │   ├── support/                      builders e classes-base de teste
        │   └── <feature>/                    espelha a estrutura de main/
        └── resources/
            └── application-test.yml          configuração usada nos testes
```

### `config/` e `common/` — transversais

Declarados como módulos abertos no Modulith: qualquer módulo pode usá-los.

| Caminho | O que fará |
|---|---|
| `config/SecurityConfig.java` | Define a cadeia de filtros do Spring Security, rotas públicas e a exigência de JWT no resto. |
| `config/JwtAuthenticationFilter.java` | Lê o token de cada requisição, valida e popula o `SecurityContext`. |
| `config/OpenApiConfig.java` | Metadados do springdoc e esquema de segurança exibido no Swagger. |
| `config/JacksonConfig.java` | Serialização de datas em UTC/ISO-8601. |
| `common/error/GlobalExceptionHandler.java` | Único ponto que traduz exceção de domínio em resposta HTTP. |
| `common/error/ApiError.java` | Formato padrão do corpo de erro devolvido pela API. |
| `common/domain/BaseEntity.java` | Id, `createdAt` e `updatedAt` herdados pelas entidades. |

### `auth/` — autenticação

| Caminho | O que fará |
|---|---|
| `auth/AuthFacade.java` | API pública: resolve o usuário autenticado para os demais módulos. |
| `auth/web/v1/AuthController.java` | Endpoints de login e refresh de token. |
| `auth/service/JwtService.java` | Emite e valida os tokens assinados. |
| `auth/service/AuthService.java` | Confere credenciais e monta o token com papel e equipes do usuário. |

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
| `V1__create_users_and_teams.sql` | Tabelas de usuário, equipe e vínculo. |
| `V2__create_tickets.sql` | Tabela de tickets, com a constraint que impede os dois modos de atribuição ao mesmo tempo. |
| `V3__create_comments_and_audit.sql` | Comentários e eventos de auditoria. |
| `V4__create_sla.sql` | Políticas de SLA, calendário comercial e horários customizados. |
| `V5__create_event_publication.sql` | Tabela do registro de publicação de eventos do Spring Modulith, que garante reprocessamento de listener que falhou. |

Nomes sujeitos a ajuste conforme a implementação avança.

### `src/test/` — testes do backend

A estrutura espelha a de `main/`: o teste de `ticket/service/TicketService.java` fica em
`ticket/service/TicketServiceTest.java`. A estratégia — o que se escreve antes e o que
se escreve depois — está em [CLAUDE.md](CLAUDE.md#testes).

| Caminho | O que fará |
|---|---|
| `ModularityTest.java` | Roda `ApplicationModules.verify()`: quebra o build se um módulo importar classe interna de outro ou se houver ciclo. Também gera os diagramas de módulo. |
| `support/IntegrationTest.java` | Anotação-base que sobe o contexto Spring e o container Postgres reaproveitado entre classes. |
| `support/TicketBuilder.java` | Monta tickets para teste em uma linha, escondendo o setup de equipe, solicitante e SLA. |
| `support/UserBuilder.java`, `support/TeamBuilder.java` | Mesmo papel para usuários, equipes e vínculos. |
| `ticket/service/` | Testes unitários das regras de transição, atribuição e acesso. |
| `ticket/domain/TicketRepositoryContractTest.java` | Testes de contrato escritos contra a **interface** do repositório. Qualquer adaptador futuro roda esta mesma suíte sem reescrita. |
| `ticket/infra/TicketSpecificationsIT.java` | **Escrito antes da implementação.** Verifica contra Postgres real que a listagem não devolve ticket que o usuário não pode ver. |
| `ticket/web/v1/` | Testes de controller com MockMvc: status HTTP, formato de erro e serialização. |
| `sla/service/SlaClockTest.java` | Casos de borda do relógio: virada de expediente, fim de semana e tempo parado em `WAITING_CUSTOMER`. |

---

## Frontend — `frontend/`

A unidade de organização é o **módulo**. As convenções — atomic design em quatro níveis,
separação entre rota e tela, CSS Modules com tokens — estão em
[CLAUDE.md](CLAUDE.md#convenções--frontend).

```
frontend/
├── package.json                 scripts e dependências
├── next.config.mjs              configuração do Next
├── .env.example                 modelo de variáveis
└── src/
    ├── app/                     rotas (App Router)
    ├── modules/                 um diretório por módulo, mais shared
    ├── lib/                     infraestrutura transversal
    ├── styles/                  tokens e estilos globais
    └── types/                   tipos gerados do OpenAPI
```

### `src/app/` — rotas

Cada `page.tsx` resolve autenticação, busca os dados e define `metadata`, depois entrega
a composição ao componente de página do módulo.

| Caminho | O que fará |
|---|---|
| `app/layout.tsx` | Layout raiz: fontes, providers e estilos globais. |
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
| `components/atoms/StatusBadge/` | Traduz status em rótulo e cor. |
| `components/atoms/PriorityTag/` | Traduz prioridade em rótulo e cor. |
| `actions/` | Server Actions de escrita: abrir, transicionar, comentar e atribuir. |
| `services/` | Leitura da API de tickets, chamada a partir do servidor. |
| `types/` | Tipos de UI do módulo. Os de contrato vêm de `src/types/api.d.ts`. |

`modules/team/`, `modules/user/` e `modules/auth/` seguem a mesma forma interna.

### `src/modules/shared/`

Mesma estrutura interna dos demais módulos, para o que é usado por mais de um.

| Caminho | O que fará |
|---|---|
| `components/atoms/` | Primitivos: `Button`, `Input`, `Select`. |
| `components/molecules/` | `Modal`, `Pagination`, `EmptyState`. |
| `hooks/` | Hooks usados por mais de um módulo. |
| `utils/` | Formatação de data, texto e número. |

### `src/lib/`, `src/styles/` e `src/types/`

| Caminho | O que fará |
|---|---|
| `lib/http/client.ts` | Cliente HTTP do lado servidor; anexa o token e traduz erro da API. |
| `lib/auth/session.ts` | Lê e valida a sessão a partir do cookie, para uso em Server Components. |
| `lib/permissions.ts` | Espelha as regras de visibilidade **apenas** para mostrar ou esconder botões. A decisão real é sempre do backend. |
| `styles/tokens.css` | Design tokens em custom properties. Todo `styles.module.css` consome daqui; valor solto é proibido. |
| `styles/globals.css` | Reset e estilos de documento. |
| `types/api.d.ts` | Tipos gerados do schema OpenAPI. Não edite à mão. |

---

## E2E — `e2e/`

Roda contra a stack completa subida pelo Docker Compose, não contra mocks.

| Caminho | O que fará |
|---|---|
| `e2e/playwright.config.ts` | Aponta para a stack local, configura browsers, retries e trace em caso de falha. |
| `e2e/specs/` | Um arquivo por fluxo de usuário (abrir chamado, atender, atribuir, resolver). |
| `e2e/fixtures/` | Sessões pré-autenticadas por papel, para não repetir login em cada teste. |
| `e2e/seed/` | Carga inicial de dados usada pelos cenários. |

---

## Infraestrutura — `docker/`

| Caminho | O que fará |
|---|---|
| `docker/docker-compose.yml` | Sobe Postgres, backend e frontend para desenvolvimento local. |
| `docker/backend.Dockerfile` | Build multi-stage do backend. |
| `docker/frontend.Dockerfile` | Build do frontend em modo standalone. |

---

## Documentação — `docs/`

| Caminho | O que fará |
|---|---|
| `docs/adr/` | Registros de decisão de arquitetura, um arquivo por decisão. |
| `docs/domain-model.md` | Diagrama de entidades e detalhamento das regras de atribuição. |
| `docs/modules/` | Diagramas de módulo gerados pelo Spring Modulith. Saída de build, não escrita à mão. |
