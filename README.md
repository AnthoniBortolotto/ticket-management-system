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
| Auth | JWT HS256 validado pelo resource server do Spring Security, refresh token revogável em banco, cookie `httpOnly` no Next.js |
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

> **Estado atual: identidade e autenticação prontas.** Login com JWT, sessão
> revogável, bloqueio por força bruta e gestão mínima de usuários funcionam de ponta a
> ponta. **Ainda não há equipes, tickets nem tela.** O que existe e o que falta está
> separado em [CODEBASE-MAP.md](CODEBASE-MAP.md); as decisões de autenticação, no
> [ADR 0003](docs/adr/0003-autenticacao-jwt.md).

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

# 2. variaveis do backend (uma vez)
cp backend/.env.example backend/.env

# 3. backend (em outro terminal) — sobe no perfil `dev`, que carrega a base de demonstracao
cd backend && ./mvnw spring-boot:run

# 4. frontend (em outro terminal)
cd frontend && pnpm install && pnpm dev
```

#### Contas de demonstração

O perfil `dev` semeia o elenco abaixo. Todas as contas de demonstração usam a senha
`demo123`; o administrador usa a senha correspondente ao `ADMIN_PASSWORD_HASH` do seu
`.env` (`admin123`, no exemplo). São credenciais públicas de brinquedo — em qualquer
ambiente que não seja a sua máquina, gere outro hash.

| Conta | Papel | Equipes |
|---|---|---|
| `admin@ticketsystem.local` | `ADMIN` | — |
| `lead.suporte@ticketsystem.local` | `AGENT` | Suporte N1 (`LEAD`) |
| `agente.suporte@ticketsystem.local` | `AGENT` | Suporte N1 (`MEMBER`) |
| `lead.infra@ticketsystem.local` | `AGENT` | Infraestrutura (`LEAD`) |
| `agente.infra@ticketsystem.local` | `AGENT` | Infraestrutura (`MEMBER`) |
| `agente.polivalente@ticketsystem.local` | `AGENT` | Suporte N1 e Infraestrutura (`MEMBER`) |
| `ana.solicitante@ticketsystem.local`, `bruno.solicitante@ticketsystem.local` | `REQUESTER` | — |

#### Chamando a API autenticada

```bash
# 1. login: devolve o access token (60 min) e o refresh token (7 dias)
curl -s -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@ticketsystem.local","password":"admin123"}'

# 2. usar o access token
curl -s localhost:8080/api/v1/users/1 -H "Authorization: Bearer $ACCESS_TOKEN"

# 3. renovar: o refresh token usado deixa de valer, e reapresenta-lo derruba a sessao
curl -s -X POST localhost:8080/api/v1/auth/refresh \
  -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"
```

Cinco senhas erradas seguidas bloqueiam a conta por 15 minutos (`423`). O contrato
completo está em `http://localhost:8080/swagger-ui.html`.

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

Duas são obrigatórias, e nenhuma das duas tem default no código:

- `ADMIN_PASSWORD_HASH`, porque a migration que cria o primeiro administrador precisa de
  um hash;
- `JWT_SECRET`, porque um default aqui significaria assinar tokens com uma chave
  publicada no repositório.

As demais têm defaults em `application.yml` e no Compose que combinam entre si.

Para ser exato sobre o que isso protege: o `.env.example` e o Secret do Kubernetes **têm**
valores de desenvolvimento publicados, de propósito, para o projeto rodar sem setup. Quem
segue este README termina com `admin` / `admin123` e uma chave de assinatura conhecida,
e isso é sabido. O que a ausência de default garante é que um ambiente que não copiou
nenhum desses arquivos **não sobe**, em vez de subir com credenciais públicas sem
ninguém perceber.

```bash
cp backend/.env.example backend/.env         # necessario; traz os valores obrigatorios de exemplo
cp frontend/.env.example frontend/.env.local # o Next le .env.local nativamente
```

Se o backend não subir com `violates check constraint "users_password_hash_is_bcrypt"`
ou com `ticketsystem.auth.secret nao esta definido`, é este arquivo que está faltando. As
duas falhas são propositais: sem elas, a aplicação subiria com um administrador cuja
senha não é hash nenhum, ou assinando tokens com uma chave vazia.

| Variável | Onde | Descrição |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | backend | Conexão com o Postgres |
| `SERVER_PORT`, `LOG_LEVEL` | backend | Porta e verbosidade |
| `ADMIN_PASSWORD_HASH` | backend | **Obrigatória.** Hash BCrypt da senha do primeiro administrador. O SQL nunca vê senha em texto |
| `ADMIN_EMAIL` | backend | E-mail desse administrador. Tem default. O nome não é variável: vai literal na migration, porque placeholder de Flyway não escapa nada |
| `JWT_SECRET` | backend | **Obrigatória.** Chave HS256 dos tokens: texto de pelo menos 32 bytes, lido como está (não é base64). O boot recusa valor curto, vazio ou o placeholder não resolvido |
| `JWT_ISSUER` | backend | Emissor gravado no token. Default `ticket-system` |
| `JWT_EXPIRATION_MINUTES` | backend | Validade do access token. Default 60 |
| `JWT_REFRESH_DAYS` | backend | Validade do refresh token. Default 7 |
| `LOGIN_MAX_FAILED_ATTEMPTS` | backend | Falhas seguidas que bloqueiam a conta. Default 5 |
| `LOGIN_LOCK_MINUTES` | backend | Duração do bloqueio, e janela em que falhas contam como seguidas. Default 15 |
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
