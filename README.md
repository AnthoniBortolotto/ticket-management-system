# Ticket Management System

Sistema de gerenciamento de tickets (helpdesk / service desk) com atribuição por
equipe, controle de visibilidade por papel e SLA calculado em horário comercial.

> Projeto de portfólio: o objetivo é demonstrar arquitetura limpa, testes e
> documentação — não apenas fazer o CRUD funcionar.

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 25 + Spring Boot 4.1 (WebMVC, Data JPA, Security, Validation) + Maven |
| Modularidade | Spring Modulith 2.1 — fronteiras entre módulos verificadas por teste |
| Banco | PostgreSQL 16 + Flyway (migrations versionadas) |
| Frontend | Next.js 16 (App Router) + React 19 + TypeScript 5.9 + CSS Modules |
| Auth | JWT próprio no Spring Security, cookie `httpOnly` no Next.js |
| Contrato | OpenAPI gerado pelo springdoc 3.1; tipos TS derivados do schema |
| Testes | JUnit 5 + Testcontainers, Vitest + Testing Library, Playwright |
| Infra local | Docker Compose (Postgres + backend + frontend); `k8s/` para deploy |

As versões e as armadilhas de compatibilidade que elas trazem estão em
[docs/adr/0001-versoes-da-stack.md](docs/adr/0001-versoes-da-stack.md).

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
├── docker/           Compose e Dockerfiles do ambiente local
├── k8s/              Manifests de deploy (demonstração; o dia a dia é o Compose)
├── docs/             ADRs e diagramas de módulo gerados pelo build
├── CLAUDE.md         Instruções para agentes de IA que trabalham no repo
└── CODEBASE-MAP.md   Mapa navegável do código — o que existe e onde
```

> **Estado atual: esqueleto.** Build, testes, Docker e CI estão de pé; **nenhuma regra
> de negócio foi implementada ainda** — não há entidade, migration, endpoint nem tela.
> O que existe e o que falta está separado em [CODEBASE-MAP.md](CODEBASE-MAP.md).

O backend organiza-se por feature (`ticket/`, `team/`, `sla/`), com a versão da API
apenas na camada web. O frontend organiza-se por módulo, com atomic design dentro de
cada um. As duas decisões estão detalhadas em [CLAUDE.md](CLAUDE.md).

Para saber onde cada coisa mora, leia [CODEBASE-MAP.md](CODEBASE-MAP.md).

## Pré-requisitos

- JDK 25 (o Maven vem pelo wrapper, `./mvnw`)
- Node.js 24 e pnpm 9
- Docker e Docker Compose
- Opcional, só para os manifests de Kubernetes: `kind` e `kubectl`

> **Rede com inspeção TLS.** Se `./mvnw` falhar com `PKIX path building failed`, um
> antivírus ou proxy está interceptando HTTPS e a CA dele não está no truststore do JDK.
> No Windows, contorne com
> `export MAVEN_OPTS="-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT"`. Isso não resolve
> `docker build`, que tem truststore próprio — nesse caso desligue a inspeção TLS do
> antivírus.

## Como rodar

### Subindo tudo com Docker

```bash
docker compose -f docker/docker-compose.yml up -d
```

Backend em `http://localhost:8080`, frontend em `http://localhost:3000`,
Postgres em `localhost:5432`.

> ⚠️ **As imagens de backend e frontend ainda não foram construídas com sucesso.** Em
> rede com inspeção TLS o build falha ao baixar dependências dentro do container (ver
> os pré-requisitos acima). O caminho de desenvolvimento abaixo — banco no Docker,
> aplicações na máquina — está validado e é o recomendado.

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

### Rodando em Kubernetes (opcional)

Demonstração de deploy sobre as mesmas imagens do Compose. Não é o ambiente de
desenvolvimento — o porquê está no
[ADR 0002](docs/adr/0002-infra-local-compose-e-kubernetes.md).

```bash
kind create cluster --config k8s/kind-config.yaml
docker compose -f docker/docker-compose.yml build
kind load docker-image ticket-system/backend:local ticket-system/frontend:local --name ticket-system
kubectl apply -k k8s/base
```

O Ingress espera o controller `ingress-nginx` instalado no cluster.

## Variáveis de ambiente

Nenhuma é obrigatória para desenvolver: `application.yml` e o Compose já trazem defaults
que combinam entre si. Os arquivos de exemplo existem para quando você precisar mudar
algo.

```bash
cp backend/.env.example backend/.env         # opcional; o Compose lê se existir
cp frontend/.env.example frontend/.env.local # o Next lê .env.local nativamente
```

| Variável | Onde | Descrição |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | backend | Conexão com o Postgres |
| `SERVER_PORT`, `LOG_LEVEL` | backend | Porta e verbosidade |
| `JWT_SECRET` | backend | Chave de assinatura dos tokens (nunca commitar). Ainda não lida — entra com o módulo `auth` |
| `JWT_EXPIRATION_MINUTES` | backend | Tempo de vida do access token. Idem |
| `API_BASE_URL` | frontend | URL do backend usada pelo servidor Next.js. Não é `NEXT_PUBLIC_`: o browser nunca fala com o backend direto |

## Testes

```bash
cd backend && ./mvnw test                      # unitários + integração (Testcontainers)
cd backend && ./mvnw verify                    # testes + cobertura JaCoCo
cd backend && ./mvnw pitest:mutationCoverage   # mutation testing
cd frontend && pnpm test                       # Vitest + Testing Library
cd frontend && pnpm lint                       # ESLint
cd frontend && pnpm typecheck                  # tsc --noEmit
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
