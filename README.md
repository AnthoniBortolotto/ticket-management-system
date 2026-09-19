# Ticket Management System

Sistema de gerenciamento de tickets (helpdesk / service desk) com atribuição por
equipe, controle de visibilidade por papel e SLA calculado em horário comercial.

> Projeto de portfólio: o objetivo é demonstrar arquitetura limpa, testes e
> documentação — não apenas fazer o CRUD funcionar.

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 25 + Spring Boot 4 (Web, Data JPA, Security, Validation) + Maven |
| Modularidade | Spring Modulith — fronteiras entre módulos verificadas por teste |
| Banco | PostgreSQL 16 + Flyway (migrations versionadas) |
| Frontend | Next.js 14+ (App Router) + TypeScript + React Server Components + CSS Modules |
| Auth | JWT próprio no Spring Security, cookie `httpOnly` no Next.js |
| Contrato | OpenAPI gerado pelo springdoc; tipos TS derivados do schema |
| Infra local | Docker Compose (Postgres + backend + frontend) |

## Funcionalidades

**No escopo**

- Ciclo de vida completo do ticket (abertura → triagem → atendimento → resolução)
- Equipes: todo usuário pertence a uma equipe; tickets são atribuídos a equipes
- Atribuição exclusiva a um usuário, com regras próprias de visibilidade
- SLA por prioridade, com relógio que pausa em espera e só corre em horário útil
- Histórico de auditoria imutável de cada mudança de estado

**Fora do escopo por enquanto** — notificações por e-mail, anexos de arquivo e
dashboard de métricas. O modelo de dados não deve impedir essas adições.

## Modelo de domínio

### Entidades

| Entidade | Responsabilidade |
|---|---|
| `User` | Pessoa que usa o sistema. Papel global: `REQUESTER`, `AGENT` ou `ADMIN`. |
| `Team` | Agrupa agentes. Um ticket pertence a uma equipe enquanto está em atendimento. |
| `TeamMembership` | Liga usuário e equipe, com papel `MEMBER` ou `LEAD`. |
| `Ticket` | O chamado: status, prioridade, categoria, solicitante, equipe e responsável. |
| `Comment` | Interação no ticket. Pode ser pública ou interna (visível só para agentes). |
| `AuditEvent` | Registro imutável de cada transição de estado ou reatribuição. |
| `SlaPolicy` | Prazo de primeira resposta e de resolução por prioridade. |
| `WorkSchedule` | Horário de trabalho. Global (comercial) ou customizado por usuário. |

### Atribuição e visibilidade

Um ticket está sempre em **um** dos dois modos de atribuição:

**Modo equipe** — o ticket pertence à equipe.

- Qualquer membro da equipe vê o ticket e pode atuar nele.
- Um membro pode ser marcado como **responsável atual**, mas o ticket continua
  pertencendo à equipe e qualquer membro pode assumir o lugar dele.

**Modo exclusivo** — o ticket foi atribuído diretamente a um usuário.

- A equipe de origem **perde** o acesso.
- Enxergam o ticket: o responsável, o solicitante, o líder da equipe de origem e admins.
- Podem devolvê-lo à equipe: o responsável, o líder da equipe de origem e admins.

O solicitante sempre enxerga o próprio ticket, em qualquer modo, mas nunca vê
comentários internos.

### Fluxo de status

```
OPEN → IN_PROGRESS → WAITING_CUSTOMER → RESOLVED → CLOSED
                ↑__________________|        |
                                            ↓
                                        REOPENED → IN_PROGRESS
```

`REOPENED` é alcançável a partir de `RESOLVED` e de `CLOSED`.

### SLA

Cada prioridade define um prazo de primeira resposta e um de resolução (valores a
definir). O relógio do SLA:

- **Pausa** enquanto o status é `WAITING_CUSTOMER`.
- **Corre apenas em horário de trabalho.** Usa o horário customizado do responsável
  atual, se ele tiver um cadastrado; caso contrário, usa o horário comercial global.

## Estrutura do repositório

```
.
├── backend/          API REST em Spring Boot
├── frontend/         Aplicação Next.js
├── e2e/              Suíte Playwright, que roda contra a stack completa
├── docker/           Compose e arquivos de infraestrutura local
├── docs/             Documentação de arquitetura e decisões
├── CLAUDE.md         Instruções para agentes de IA que trabalham no repo
└── CODEBASE-MAP.md   Mapa navegável do código — o que existe e onde
```

O backend organiza-se por feature (`ticket/`, `team/`, `sla/`), com a versão da API
apenas na camada web. O frontend organiza-se por módulo, com atomic design dentro de
cada um. As duas decisões estão detalhadas em [CLAUDE.md](CLAUDE.md).

Para saber onde cada coisa mora, leia [CODEBASE-MAP.md](CODEBASE-MAP.md).

## Pré-requisitos

- JDK 25
- Node.js 20+ e pnpm (ou npm)
- Docker e Docker Compose

## Como rodar

### Subindo tudo com Docker

```bash
docker compose -f docker/docker-compose.yml up -d
```

Backend em `http://localhost:8080`, frontend em `http://localhost:3000`,
Postgres em `localhost:5432`.

### Desenvolvimento local (hot reload)

Suba só o banco e rode as aplicações na máquina:

```bash
# 1. banco
docker compose -f docker/docker-compose.yml up -d postgres

# 2. backend (em outro terminal)
cd backend && ./mvnw spring-boot:run

# 3. frontend (em outro terminal)
cd frontend && pnpm install && pnpm dev
```

## Variáveis de ambiente

```bash
cp backend/.env.example backend/.env
cp frontend/.env.example frontend/.env.local
```

| Variável | Onde | Descrição |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | backend | Conexão com o Postgres |
| `JWT_SECRET` | backend | Chave de assinatura dos tokens (nunca commitar) |
| `JWT_EXPIRATION_MINUTES` | backend | Tempo de vida do access token |
| `API_BASE_URL` | frontend | URL do backend usada pelo servidor Next.js |

## Testes

```bash
cd backend && ./mvnw test                      # unitários + integração (Testcontainers)
cd backend && ./mvnw verify                    # testes + cobertura JaCoCo
cd backend && ./mvnw pitest:mutationCoverage   # mutation testing
cd frontend && pnpm test                       # Vitest + Testing Library
cd frontend && pnpm lint                       # ESLint
pnpm --dir e2e test                            # Playwright, com a stack de pé
```

O projeto é desenvolvido com teste primeiro no nível unitário e nas regras que só
existem quando o banco executa a query. A estratégia completa está em
[CLAUDE.md](CLAUDE.md#testes).

## Documentação da API

Com o backend rodando:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Schema OpenAPI: `http://localhost:8080/v3/api-docs`

## Licença

Ver [LICENSE](LICENSE).
