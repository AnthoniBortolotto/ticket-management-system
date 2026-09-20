# Roadmap — do esqueleto à aplicação completa

O que falta para o sistema atender os requisitos do [README](../README.md) e as regras
do [CLAUDE.md](../CLAUDE.md). Este é o **plano de trabalho**; o que já existe no disco
está no [CODEBASE-MAP](../CODEBASE-MAP.md), que continua sendo a fonte de verdade sobre
o presente.

Cada item concluído sai daqui e entra no CODEBASE-MAP na mesma alteração. Lista que
acumula itens marcados vira arquivo morto.

**Definition of done vale para todo item:** ver
[CLAUDE.md](../CLAUDE.md#definition-of-done-de-uma-feature). Não há CI — os comandos são
a única rede.

---

## Decisões já tomadas

Estas saíram de discussão e não devem ser reabertas sem motivo novo.

| Decisão | Escolha | Onde está registrada |
|---|---|---|
| Conclusão de evento | `archive` — o concluído sai da tabela ativa | `V1__create_event_publication.sql` |
| Corpo de erro | `ProblemDetail` (RFC 9457), não formato próprio | Já implementado |
| Validação de JWT | `spring-boot-starter-oauth2-resource-server` (Nimbus), não filtro escrito à mão | Fase 2 abaixo |
| Primeiro usuário | Migration de seed, com dados de demonstração em location separada por perfil | Fase 1 abaixo |
| Infra local | Compose desenvolve, Kubernetes demonstra | [ADR 0002](adr/0002-infra-local-compose-e-kubernetes.md) |
| Versões da stack | Ver tabela e as seis armadilhas | [ADR 0001](adr/0001-versoes-da-stack.md) |

## Premissas de produto

Assumidas para destravar a implementação. **Cada uma é barata de mudar agora e cara
depois** — se alguma estiver errada, corrija antes da fase correspondente.

- **Prioridades:** `LOW`, `MEDIUM`, `HIGH`, `URGENT`. Prazos default (primeira resposta
  / resolução, em horas úteis): 8/40, 4/24, 2/8, 1/4. Ficam em `SlaPolicy`, no banco —
  nunca em Java.
- **Categoria do ticket:** enum `HARDWARE`, `SOFTWARE`, `ACCESS`, `OTHER`. Valor novo em
  enum não quebra contrato, então dá para crescer sem `v2`.
- **Horário comercial global:** segunda a sexta, 09:00–18:00, `America/Sao_Paulo`.
  Feriados ficam fora do escopo — registre como limitação conhecida.
- **Listagem de tickets:** paginada com `Page` do Spring Data, 20 por página, ordenada
  por prioridade e depois por prazo de SLA mais próximo.
- **Senhas:** BCrypt via `PasswordEncoder` do Spring Security.

---

## Fase 0 — Fechar o esqueleto

Itens pequenos que sobraram do setup e atrapalham se ficarem para depois.

- [x] Registrar o módulo de eventos do Modulith: `spring-modulith-starter-jpa` e a
      migration `V1__create_event_publication.sql`, em modo `archive`.
      `EventPublicationIT` prova o caminho inteiro.
- [x] Diretórios vazios apagados. Cada pasta nasce com o primeiro arquivo dela.
- [ ] Validar `docker compose build`. Hoje **nunca rodou** — o Avast intercepta TLS e o
      truststore do container não conhece a CA dele. Enquanto não rodar, os dois
      Dockerfiles são código não exercitado.

## Fase 1 — Fundação de dados e de teste

Nada nas fases seguintes é testável sem isto. É a fase que mais economiza tempo depois.

- [ ] `V2__create_users_and_teams.sql`: `users`, `teams`, `team_memberships`. Papel
      global no usuário, papel `MEMBER`/`LEAD` no vínculo.
- [ ] Seed do admin em migration versionada, senha vinda de variável de ambiente — nunca
      literal no SQL.
- [ ] Seed de demonstração (equipes, agentes, solicitantes) em location Flyway separada,
      ativada só nos perfis `dev` e `test` via `spring.flyway.locations`.
- [ ] `support/UserBuilder`, `support/TeamBuilder`: montam o cenário em uma linha. Sem
      eles, o setup de cada teste de visibilidade vira 40 linhas ilegíveis e as pessoas
      param de escrever teste.

## Fase 2 — `user` e `auth`

Identidade primeiro: toda regra de visibilidade depende de saber quem está pedindo.

- [ ] `user/domain/`: `User`, `UserRole`, `UserRepository` (interface em linguagem de
      negócio). `WorkSchedule` pode esperar a Fase 6.
- [ ] `user/infra/JpaUserRepository`: adapta Spring Data para a interface de domínio. O
      service nunca importa `org.springframework.data`.
- [ ] `user/UserFacade`: consulta por id, para os outros módulos. **Anote o
      `package-info.java` com `@ApplicationModule` agora** — só funciona com a primeira
      classe no pacote.
- [ ] `auth`: `AuthService` confere credenciais; emissão do token com `JwtEncoder`
      (chave RSA ou HMAC vinda de configuração). Claims: subject, papel, equipes.
- [ ] `SecurityConfig`: trocar o "nega tudo" por `oauth2ResourceServer(jwt)`, mapeando os
      claims para authorities. `/api/v1/auth/login` fica público.
- [ ] `auth/web/v1/AuthController`: login e refresh.
- [ ] **Testes de segurança que não podem faltar:** token expirado → 401; assinatura
      inválida → 401; token sem o papel exigido → 403; rota pública sem token → 200.
- [ ] Proteção contra força bruta no login: hoje `/api/v1/auth/login` aceitaria
      tentativas ilimitadas. Decidir entre bloqueio por tentativas na conta ou limite por
      IP — e testar que o bloqueio dispara.
- [ ] Decidir se `/swagger-ui` e `/v3/api-docs` continuam públicos. Hoje estão, e num
      projeto de portfólio isso é proposital; a alternativa é restringi-los fora do
      perfil de desenvolvimento. É uma decisão, não um esquecimento — registre qual.

## Fase 3 — `team`

`ticket` depende dele para decidir visibilidade, então vem antes.

- [ ] `team/domain/`: `Team`, `TeamMembership`, `TeamMembershipRepository`.
- [ ] `team/service/TeamService`: criar equipe, adicionar e remover membro, promover a
      `LEAD`.
- [ ] `team/TeamFacade`: responde "é membro?" e "quem lidera?". É a única porta que
      `ticket` enxerga.
- [ ] `team/web/v1/TeamController` + DTOs `record`.
- [ ] Teste de integração do caso negativo: quem não é admin nem `LEAD` não gerencia
      membros.

## Fase 4 — `ticket`: núcleo

A maior fase. Vale quebrar em commits por sub-bloco.

- [ ] `V3__create_tickets.sql`, com a **constraint que impede os dois modos de atribuição
      ao mesmo tempo**. A regra é do banco, não só do Java — e a constraint tem teste de
      integração próprio, escrito **antes**, porque só existe quando o Postgres executa.
- [ ] `ticket/domain/TicketStatus`: o enum **e** as transições permitidas. TDD estrito:
      toda transição inválida rejeitada, `REOPENED` alcançável de `RESOLVED` e de
      `CLOSED`.
- [ ] `ticket/domain/TicketPriority`, `ticket/domain/Ticket`, `ticket/domain/TicketRepository`.
- [ ] `ticket/domain/Comment` com a flag interno/público.
- [ ] `ticket/service/TicketService`: abrir, transicionar, comentar. Publica
      `TicketStatusChanged`.
- [ ] `ticket/infra/`: `SpringDataTicketRepository` + `JpaTicketRepository`.
- [ ] `ticket/domain/TicketRepositoryContractTest`: testes escritos contra a
      **interface**. É o que permite trocar o armazenamento depois sem reescrever teste.
- [ ] `ticket/web/v1/`: controller e DTOs. Entidade JPA nunca cruza a fronteira HTTP.

## Fase 5 — Visibilidade e atribuição

**A parte mais cara de errar do sistema inteiro.** Listar ticket de outra equipe é
vazamento de dados, não bug de tela.

- [ ] `ticket/infra/TicketSpecificationsIT` — **escrito antes da implementação**, contra
      Postgres real. Um caso por linha da tabela de visibilidade do CLAUDE.md, cada um
      afirmando o que a pessoa **não** vê.
- [ ] `ticket/infra/TicketSpecifications`: a regra em SQL. Filtro na query, nunca em
      memória depois de carregar tudo.
- [ ] `ticket/service/TicketAccessPolicy`: a mesma regra para um ticket já carregado.
      **As duas precisam concordar** — se divergirem, alguém vê o que não deveria.
- [ ] `ticket/service/TicketAssignmentService`: troca entre modo equipe e modo exclusivo.
      Ao ir para exclusivo, preserva `origin_team_id`; ao voltar, valida que quem pede é
      o responsável, o líder da equipe de origem ou um admin.
- [ ] `TicketAssignmentChanged` publicado a cada reatribuição.
- [ ] Comentário interno nunca chega ao solicitante — teste de integração pelo endpoint,
      não só unitário do service.

## Fase 6 — `sla` e `audit`

Os dois escutam eventos. `ticket` não sabe que eles existem, e é isso que faz notificação
ser só mais um listener no futuro.

- [ ] `V4__create_comments_and_audit.sql`, `V5__create_sla.sql`.
- [ ] `audit/domain/AuditEvent` **append-only**: sem `update`, sem `delete`. O repositório
      não expõe esses métodos — a regra é estrutural, não de disciplina.
- [ ] `audit/service/TicketEventListener` com `@ApplicationModuleListener`.
- [ ] `audit/AuditFacade`: linha do tempo de um ticket.
- [ ] `user/domain/WorkSchedule` + `sla/domain/BusinessCalendar`.
- [ ] `sla/service/SlaClock`: converte duração em prazo real. **É onde moram os bugs
      difíceis** — vire de expediente, fim de semana, tempo parado em
      `WAITING_CUSTOMER`, responsável com horário customizado, responsável trocando no
      meio do prazo. Cada um é um caso de teste.
- [ ] `sla/domain/SlaPolicy` e `sla/service/SlaService`; `TicketEventListener` recalcula.
- [ ] `sla/SlaFacade`: prazos de um ticket, para exibição.
- [ ] Rodar `./mvnw pitest:mutationCoverage` sobre `SlaClock` e `TicketStatus`. São as
      classes onde cobertura alta engana mais.

## Fase 7 — Frontend: sessão e leitura

- [ ] `pnpm gen:api` com a API completa; `src/types/api.d.ts` deixa de ser vazio. Repetir
      a cada endpoint novo.
- [ ] `app/api/auth/[...]/route.ts`: faz login contra o backend e guarda o JWT em cookie
      `httpOnly`. O browser nunca vê o token.
- [ ] `lib/auth/session.ts`: lê e valida a sessão para Server Components.
- [ ] `app/(auth)/login/page.tsx` + `modules/auth/components/pages/LoginPage`.
- [ ] `(app)/layout.tsx`: moldura do sistema e verificação de sessão em **um** lugar, não
      repetida em cada rota.
- [ ] Atoms `StatusBadge` e `PriorityTag`, consumindo os tokens `--color-status-*` e
      `--color-priority-*` que já existem.
- [ ] `molecules/SlaIndicator`: tempo restante, destacando o que estourou.
- [ ] `organisms/TicketTable` e `components/pages/TicketListPage` — a tela recebe **tudo**
      por props e não busca dado nenhum.
- [ ] `app/(app)/tickets/page.tsx`: a rota busca e delega.
- [ ] `lib/permissions.ts`: espelha a visibilidade **só** para mostrar ou esconder botão.
      A decisão real é sempre do backend — comentar isso no arquivo.

## Fase 8 — Frontend: escrita

- [ ] `modules/ticket/actions/`: Server Actions de abrir, transicionar, comentar e
      atribuir, com `'use server'` no topo.
- [ ] `organisms/CommentThread`: esconde comentário interno do solicitante. O backend já
      não manda — a tela não pode ser a única barreira.
- [ ] `organisms/AssignmentPanel`: ações respeitando o que o usuário atual pode fazer.
- [ ] `components/pages/TicketDetailPage` e a rota `tickets/[id]`.
- [ ] `tickets/new` e a tela de abertura.
- [ ] `modules/team/`: tela de equipes e membros.
- [ ] Testes com MSW nos componentes que falam com a API: o componente é testado sem
      saber que está mockado.

## Fase 9 — E2E e fechamento

- [ ] **Escrever os cenários em texto antes de implementar** — é critério de aceite, não
      teste: solicitante abre chamado; agente assume e resolve; líder atribui em modo
      exclusivo; responsável devolve à equipe; solicitante reabre.
- [ ] `e2e/seed/`: carga inicial dos cenários.
- [ ] `e2e/fixtures/`: sessão pré-autenticada por papel, para não repetir login.
- [ ] Um spec por fluxo em `e2e/specs/`.
- [ ] **Um E2E de vazamento:** logar como membro de outra equipe e confirmar que o ticket
      não aparece na lista nem abre pela URL direta. É o teste que justifica todo o
      desenho de visibilidade.
- [ ] Sincronizar `k8s/base/` com tudo que mudou no Compose e validar com
      `kubectl kustomize k8s/base`.
- [ ] `docs/domain-model.md`: diagrama de entidades e detalhamento da atribuição.
- [ ] README final: screenshots, credenciais de demonstração, o que está fora de escopo.

---

## Fora de escopo — não implemente

Notificações por e-mail, anexos de arquivo e dashboard de métricas. O modelo de dados
não deve impedi-los, mas eles **não entram**. Se parecerem necessários, pergunte antes.

## Riscos conhecidos

- **`SlaClock` é a classe mais difícil do projeto.** Aritmética de tempo com pausas,
  fuso e expediente erra em silêncio. Reserve tempo e cubra com mutation testing.
- **A regra de visibilidade existe em dois lugares** (`TicketAccessPolicy` e
  `TicketSpecifications`). Divergência entre elas é vazamento de dados. Toda mudança em
  uma exige revisitar a outra — considere um teste que rode os mesmos cenários pelos
  dois caminhos.
- **Não há CI.** A disciplina de rodar tudo antes de commitar é a única proteção.
- **As imagens Docker nunca foram construídas.** Podem conter erros que só aparecem na
  primeira tentativa real.
- **`event_publication_archive` cresce sem limite e guarda o evento serializado.**
  Enquanto os eventos carregarem só identificadores, é questão de disco. No dia em que
  um deles levar nome ou e-mail, vira armazenamento indefinido de dado pessoal fora das
  tabelas de domínio — decida expurgo antes disso, não depois. Está anotado no cabeçalho
  da migration.
