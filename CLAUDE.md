# CLAUDE.md

Instruções para agentes de IA que trabalham neste repositório. Leia este arquivo e o
[CODEBASE-MAP.md](CODEBASE-MAP.md) antes de qualquer alteração.

## O projeto

Sistema de gerenciamento de tickets (helpdesk), monorepo com backend Spring Boot e
frontend Next.js. É um **projeto de portfólio**: o código é lido por recrutadores e
revisores, então clareza, testes e consistência valem mais do que velocidade de
entrega. Não tome atalhos que você não defenderia em uma code review.

Visão geral funcional e instruções de execução: [README.md](README.md).

## Regra permanente: manter o CODEBASE-MAP.md atualizado

O `CODEBASE-MAP.md` é o índice navegável do código. Ele existe para que um agente
encontre o lugar certo sem varrer o repositório inteiro. **Ele só serve se estiver
correto** — um mapa desatualizado é pior do que nenhum.

### As duas partes do mapa

O arquivo é dividido em **Parte 1 — o que existe hoje** e **Parte 2 — estrutura
planejada**, e a separação é rígida:

- Na **Parte 1** só entra caminho que está no disco. É o índice de busca; se algo está
  listado ali, dá para abrir.
- Na **Parte 2** fica o desenho acordado, que ainda não existe. Ela orienta quem vai
  implementar e mantém o vocabulário consistente.

**Ao criar um arquivo de verdade, mova a linha dele da Parte 2 para a Parte 1**, na
mesma alteração. Misturar as duas destrói o valor do documento: um mapa em que existir
e não existir se parecem obriga a conferir tudo no disco, e aí não era mapa nenhum.

### Quando atualizar

Atualize o `CODEBASE-MAP.md` na mesma alteração em que você:

- criar, mover, renomear ou remover um arquivo, pacote ou diretório;
- mudar a responsabilidade de um módulo já mapeado;
- adicionar um endpoint, uma entidade, uma migration ou uma rota do frontend;
- mudar um comando de build, teste ou execução.

Ajustes que **não** exigem atualizar o mapa: mudanças internas a um arquivo que não
alteram sua responsabilidade, correções de bug, refatorações locais.

Ao atualizar, mantenha o formato existente: caminho e uma descrição de uma linha que
diga *o que aquilo faz*, não o que aquilo é. "Valida transições de status do ticket" é
útil; "classe de serviço" não é.

## Regras de domínio que não podem ser violadas

Estas regras são a razão de existir do sistema. Se uma alteração parecer exigir
quebrá-las, pare e pergunte antes de implementar.

### Atribuição

Um ticket está sempre em exatamente um dos dois modos:

- **Modo equipe** (`assigned_team_id` preenchido): todos os membros da equipe veem e
  atuam. O campo `current_assignee_id` marca quem está atuando agora, mas qualquer
  membro pode assumir o lugar dele. Trocar o responsável **não** tira o ticket da
  equipe.
- **Modo exclusivo** (`exclusive_assignee_id` preenchido): apenas aquele usuário atua.
  A equipe de origem perde o acesso, mas a referência a ela é preservada em
  `origin_team_id` — o líder dessa equipe continua enxergando o ticket.

Os dois modos são mutuamente exclusivos e isso é garantido por constraint no banco,
não apenas por validação em Java.

### Visibilidade

| Quem | Vê o quê |
|---|---|
| `ADMIN` | Tudo. |
| Solicitante | Sempre o próprio ticket, em qualquer modo. **Nunca** comentários internos. |
| Membro da equipe | Tickets em modo equipe de **qualquer equipe de que participe** — o vínculo é muitos-para-muitos. |
| Líder da equipe (`LEAD`) | Tudo das equipes que lidera, incluindo tickets que saíram delas para o modo exclusivo. |
| Responsável exclusivo | O ticket atribuído a ele. |

Toda consulta que lista ou busca tickets **precisa** aplicar esse filtro na query, não
em memória depois de carregar tudo. Filtrar depois é bug de segurança e de performance.

### Reatribuição

Devolver um ticket do modo exclusivo para a equipe: apenas o próprio responsável, o
líder da equipe de origem ou um admin.

### SLA

- O relógio **pausa** enquanto o status é `WAITING_CUSTOMER`.
- O relógio **só corre em horário de trabalho**: o horário customizado do responsável
  atual, se existir, senão o horário comercial global.
- Prazos por prioridade ficam em `SlaPolicy`, configuráveis em banco. **Nunca**
  hardcode prazos em Java ou TypeScript.

### Auditoria

Toda transição de status e toda reatribuição gera um `AuditEvent`. Registros de
auditoria são **append-only**: nunca atualize nem delete um `AuditEvent`.

## Stack e versões

As versões abaixo são as que estão no `pom.xml` e no `package.json` hoje. O porquê de
cada uma, e das duas que ficaram deliberadamente atrás do `latest`, está em
[docs/adr/0001-versoes-da-stack.md](docs/adr/0001-versoes-da-stack.md).

| Item | Versão / escolha |
|---|---|
| Java | 25 (LTS vigente) |
| Spring Boot | 4.1.1 |
| Build backend | Maven (`./mvnw`) |
| Modularidade | Spring Modulith 2.1.1 (fronteiras verificadas por teste) |
| Contrato | springdoc-openapi 3.1.1 |
| Banco | PostgreSQL 16, migrations com Flyway |
| Node | 24 (fixado em `.nvmrc` e em `engines`) |
| Frontend | Next.js 16 com App Router, React 19 |
| TypeScript | 5.9.x — **não** a 7.x, ver abaixo |
| Package manager | pnpm |

Não troque nenhuma dessas escolhas sem pedir. Em particular: **App Router, não Pages
Router**; **Maven, não Gradle**.

### Armadilhas já confirmadas — não redescubra

Estas custaram tempo e continuam valendo. As quatro de versão estão detalhadas no
[ADR 0001](docs/adr/0001-versoes-da-stack.md#quatro-armadilhas-confirmadas-na-pratica);
as demais vivem aqui e nos comentários do código que elas afetam.

- **O Boot 4 usa Jackson 3 (`tools.jackson`), não Jackson 2.** Configuração escrita com
  `com.fasterxml.jackson.*` compila e não tem efeito nenhum. A flag
  `WRITE_DATES_AS_TIMESTAMPS` saiu de `SerializationFeature` e foi para `DateTimeFeature`;
  `spring.jackson.serialization.write-dates-as-timestamps` **derruba o contexto no boot**.
  Quem cuida disso é `config/JacksonConfig.java`.
- **`spring-boot-starter-web` está deprecado** no Boot 4. Use
  `spring-boot-starter-webmvc`.
- **No Boot 4 as autoconfigurações saíram para módulos por tecnologia.** Com apenas
  `org.flywaydb:flyway-core` no classpath, a aplicação **sobe, não loga nada e não
  migra** — falha silenciosa perfeita. É preciso `spring-boot-starter-flyway`. Regra
  geral: ao adicionar uma tecnologia, prefira o starter do Boot à biblioteca crua, e
  confirme no log que ela realmente inicializou.
- **Testcontainers 2.x renomeou os módulos:** `testcontainers-postgresql` e
  `testcontainers-junit-jupiter`. Os nomes antigos param na 1.21 e o erro que aparece é
  "version is missing".
- **`@ApplicationModule` em pacote sem nenhuma classe quebra o build.** O ArchUnit falha
  ao refletir sobre um `package-info` solitário, e o estrago vaza para o
  `@SpringBootTest`, que passa a não achar a `@SpringBootConfiguration`. Os pacotes de
  módulo ainda vazios têm `package-info.java` só com javadoc: **a anotação entra junto
  com a primeira classe do módulo.**
- **TypeScript fica na 5.9.** O `eslint-config-next` depende de `typescript-eslint@8`,
  cujo peer é `<6.1.0`. Subir para a 7 quebra o `pnpm lint`, e o CLAUDE.md proíbe
  desabilitar regra de lint para o código passar.
- **Mais duas renomeações do Boot 4, que o build não acusa.** O resource server é
  `spring-boot-starter-security-oauth2-resource-server` — o nome antigo, sem `security-`,
  está deprecado e ainda resolve. E o `@AutoConfigureMockMvc` saiu de
  `spring-boot-test-autoconfigure` para `spring-boot-webmvc-test`, que não está no
  `pom.xml`: para testar a cadeia de segurança, use `support/SecureMockMvc`, que monta o
  `MockMvc` a partir do contexto sem dependência nova.
- **O Modulith não reentrega evento pendente no start, por padrão.**
  `spring.modulith.events.republish-outstanding-events-on-restart` vem `false`: um listener
  que falhe deixa a publicação pendente para sempre, sem nada no log. Está ligada no
  `application.yml`, e o `EventPublicationIT` confere que foi lida.
- **Variável de ambiente ausente num placeholder de Flyway não dá erro.** Escrever
  `placeholders.x: ${VAR}` sem default e não definir `VAR` **não** derruba o boot: o
  binder do Spring deixa o texto `${VAR}` como valor, o Flyway o substitui no SQL e a
  linha entra no banco com essa string. Foi assim que o admin quase nasceu com a senha
  `${ADMIN_PASSWORD_HASH}`. A migration `V2` fecha esse buraco com um CHECK que exige
  formato de hash em `password_hash`. Regra geral: **configuração que pode não ser lida
  precisa de algo que prove que ela foi** — uma constraint, um log, uma asserção.

### Rede com inspeção TLS

Se `./mvnw` falhar com `PKIX path building failed`, há um antivírus ou proxy
interceptando HTTPS e o truststore do JDK não conhece a CA dele. Contorno no Windows:

```bash
export MAVEN_OPTS="-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT"
```

Isso **não** resolve `docker build`: o container tem o próprio truststore e vai falhar
ao baixar do Maven Central e do registry do npm. Nesse caso a saída é desligar a
inspeção TLS do antivírus. Não comite certificado de máquina no repositório.

**Consequência prática:** as imagens de `docker/backend.Dockerfile` e
`docker/frontend.Dockerfile` nunca foram construídas com sucesso, então podem conter
erros. `docker compose up -d postgres` funciona, e é assim que se desenvolve.

## Arquitetura e versionamento

Duas coisas mudam no sistema por motivos diferentes, e cada uma tem seu mecanismo:

| O que muda | Mecanismo |
|---|---|
| O **contrato** da API — campo sai, tipo muda, semântica muda | Versão na camada web |
| A **implementação** — banco, serviço externo, algoritmo | Porta no domínio, adaptador na infra, troca por strangler |

Confundir os dois é o erro clássico. Trocar o Postgres por um serviço externo não
altera o contrato: quem chama `GET /api/v1/tickets/42` recebe exatamente a mesma
resposta. Essa troca **não** gera versão nova — gera adaptador novo.

### Estrutura de um módulo

```
com.ticketsystem/
├─ config/                      ← transversal, módulo aberto
├─ common/                      ← transversal, módulo aberto
└─ ticket/
   ├─ TicketFacade.java         ← API pública do módulo
   ├─ TicketStatusChanged.java  ← evento público
   ├─ domain/                   ← entidades, regras e a interface do repositório
   ├─ service/                  ← casos de uso; conhece só a interface
   ├─ infra/                    ← implementação do repositório (hoje JPA)
   └─ web/
      ├─ v1/                    ← controller + DTOs da v1
      └─ v2/                    ← quando existir
```

`domain`, `service` e `infra` são **únicos**, sem versão. Só `web` se multiplica.

### Fronteira entre módulos

As fronteiras são verificadas por **Spring Modulith**, não confiadas à disciplina. A
regra que ele aplica: **o que está na raiz do pacote do módulo é público; o que está em
subpacote é interno.**

Na prática, cada módulo expõe uma **fachada** na raiz do seu pacote — `TicketFacade`,
`TeamFacade` — e mais nada. `TeamService`, `JpaTicketRepository` e companhia ficam
invisíveis de fora. `config` e `common` são declarados como módulos abertos, acessíveis
por todos.

O teste `ModularityTest` roda `ApplicationModules.of(...).verify()` e **quebra o build**
se um módulo importar classe interna de outro ou se surgir ciclo entre módulos. Ele
também gera os diagramas em `docs/modules/`, que por serem saída de build nunca
desatualizam.

### Comunicação entre módulos: evento, não chamada

Quando um módulo precisa **reagir** a algo que aconteceu em outro, a comunicação é por
evento — não por chamada direta:

```java
// em sla/service/TicketEventListener.java
@ApplicationModuleListener
void quandoStatusMuda(TicketStatusChanged evento) {
    slaService.recalcular(evento.ticketId());
}
```

`ticket` publica e segue a vida; `sla` e `audit` escutam. O núcleo do domínio **não sabe
que SLA e auditoria existem**, e é por isso que adicionar notificações depois será só
mais um listener, sem tocar em `ticket`.

O Modulith grava a publicação do evento na **mesma transação** que salvou o ticket, em
tabela própria. Se o listener falhar ou a aplicação cair no meio, o evento fica marcado
como pendente e é reprocessado no próximo start — entrega confiável sem message broker.

**Chamada direta à fachada continua valendo** quando um módulo precisa *perguntar* algo
para decidir agora: `ticket` chama `TeamFacade` para saber se o usuário lidera a equipe.
A regra é: pergunta que bloqueia a decisão → fachada; consequência do que já aconteceu →
evento.

### Versão do contrato

**Todos** os módulos nascem em `/api/v1/` — `/api/v1/tickets`, `/api/v1/teams`,
`/api/v1/auth`. Nenhum endpoint fica sem versão na URL: inconsistência entre módulos é
pior do que uma versão que ninguém exercitou ainda.

Durante o desenvolvimento inicial não existe `v2`. A estrutura está pronta para receber
uma, não para exercitá-la.

**O que cria uma `v2`:** remover campo, renomear campo, mudar tipo ou mudar o
significado de um campo já publicado.

**O que não cria:** campo novo na resposta, endpoint novo, valor novo em enum,
parâmetro opcional novo. Isso entra na versão vigente.

**Quando a `v2` nascer, a `v1` para de ter lógica própria.** Controller e DTOs da `v1`
viram tradução: recebem o formato antigo, chamam o mesmo service, convertem a resposta.
Nenhuma regra de negócio pode existir em duas versões ao mesmo tempo — é assim que duas
versões passam a responder coisas diferentes sobre o mesmo ticket.

**Toda versão depreciada nasce com data de morte:** as respostas carregam
`Deprecation: true` e `Sunset: <data>` (RFC 8594). Manter uma versão viva para sempre é
não versionar, só que com mais código.

### Banco

Uma única linha de migrations Flyway, forward-only, compartilhada por todas as versões.
Versão de API nunca vira versão de schema: se a `v2` renomeia um campo, quem traduz é o
mapper de DTO da `v1`, jamais uma coluna duplicada.

### Trocar uma implementação sem quebrar nada

Este é o mecanismo para mudança estrutural — sair do Postgres para um serviço externo,
trocar o provedor de autenticação, substituir um algoritmo. Não envolve versão de API.

A condição para funcionar já está na estrutura: **o repositório é uma interface
declarada no `domain`** e o service só conhece essa interface. A implementação vive em
`infra` e é intercambiável.

Passo a passo de uma troca:

1. Escreva o adaptador novo implementando a mesma interface.
2. Rode contra ele os testes de contrato que já existem para o repositório, sem
   reescrever nenhum. É aqui que o desenho paga.
3. Suba com feature flag, atendendo uma fatia do tráfego.
4. Aumente a fatia. Se der errado, volte a flag — sem deploy.
5. Apague o adaptador antigo. Nenhum controller, service ou URL muda.

Isso é o padrão **strangler fig**. Se a troca envolver dois armazenamentos vivos ao
mesmo tempo, o plano de reconciliação de dados é escrito **antes** de o adaptador novo
subir: dois destinos de escrita sem reconciliação produzem ticket que existe de um lado
e não do outro. Migração é problema de dados, e nenhum padrão de código resolve isso
sozinho.

### Uma impureza consciente

As entidades de `domain` carregam as anotações JPA, em vez de existir uma entidade de
persistência separada. É pragmatismo: mapear tudo duas vezes custa caro e entrega pouco
num sistema deste tamanho. O ponto de troca é o **repositório**, não a entidade — um
adaptador que fale com serviço externo monta os mesmos objetos sem passar por JPA, e as
anotações ficam inertes. Se um dia isso atrapalhar de verdade, o passo seguinte é
separar entidade de domínio de entidade de persistência — mas só com motivo concreto.

## Comandos

```bash
# Backend  (antes da primeira execucao: cp backend/.env.example backend/.env)
cd backend && ./mvnw spring-boot:run           # sobe a API no perfil `dev`, com a carga de demonstracao
cd backend && ./mvnw test                      # unitários + integração
cd backend && ./mvnw verify                    # testes + cobertura JaCoCo com threshold
cd backend && ./mvnw pitest:mutationCoverage   # mutation testing (relatório em target/pit-reports)

# Frontend
cd frontend && pnpm dev                        # sobe em modo dev
cd frontend && pnpm test                       # Vitest
cd frontend && pnpm lint                       # ESLint
cd frontend && pnpm typecheck                  # tsc --noEmit

# E2E (exige a stack completa de pé)
docker compose -f docker/docker-compose.yml up -d
pnpm --dir e2e install                         # uma vez
pnpm --dir e2e exec playwright install chromium # uma vez
pnpm --dir e2e test                            # Playwright

# Infra
docker compose -f docker/docker-compose.yml up -d postgres   # só o banco (dev)
docker compose -f docker/docker-compose.yml up -d            # stack completa

# Kubernetes — demonstração de deploy, não ambiente de desenvolvimento
kind create cluster --config k8s/kind-config.yaml
docker compose -f docker/docker-compose.yml build
kind load docker-image ticket-system/backend:local ticket-system/frontend:local --name ticket-system
kubectl apply -k k8s/base
```

## Kubernetes em `k8s/`

Os manifests existem como demonstração de deploy. **O ambiente de desenvolvimento é o
Docker Compose** — o motivo está em
[docs/adr/0002-infra-local-compose-e-kubernetes.md](docs/adr/0002-infra-local-compose-e-kubernetes.md).

Como eles não são exercitados no dia a dia, nada avisa quando ficam errados. Por isso:

> **Variável de ambiente, porta, probe, imagem ou serviço que muda no
> `docker/docker-compose.yml` muda no `k8s/base/` na mesma alteração.**

Na prática, a cada alteração em `docker-compose.yml` ou nos Dockerfiles, confira:

- variável nova ou renomeada → `k8s/base/config.yaml` (ConfigMap ou Secret, conforme for
  segredo);
- porta que mudou → Deployment, Service e, se for exposta, o Ingress;
- healthcheck que mudou → as probes do Deployment correspondente;
- serviço novo → Deployment + Service novos, e a entrada no `kustomization.yaml`.

Depois de mexer, valide com `kubectl kustomize k8s/base` — ele renderiza sem cluster e
pega erro de YAML e de referência. Manifest que não renderiza é manifest quebrado.

## Convenções — backend

**Camadas.** Organize por feature, não por tipo técnico: `com.ticketsystem.ticket`,
`com.ticketsystem.team`. Dentro de cada feature: `domain` (entidade, regras e a
interface do repositório), `service` (casos de uso), `infra` (implementação do
repositório) e `web/v{n}` (controller + DTOs daquela versão). Só `config` e `common`
ficam fora de feature.

**Persistência fica atrás de interface.** `TicketRepository` é uma interface em
`domain`, com métodos que falam a linguagem do negócio. Em `infra`, uma interface
Spring Data faz o trabalho pesado e uma classe adaptadora a traduz para a interface de
domínio. O service nunca importa nada de `jakarta.persistence` nem de
`org.springframework.data` — é isso que torna a troca de armazenamento possível sem
tocar em regra de negócio.

**Controllers não contêm regra de negócio.** Eles recebem DTO, delegam ao service e
devolvem DTO. Regra de negócio mora no domínio ou no service.

**Entidades JPA nunca cruzam a fronteira HTTP.** Sempre converta para DTO. Use
`record` para DTOs.

**Validação** com Bean Validation (`@Valid`) nos DTOs de entrada. Regras que dependem
de estado do sistema ficam no service.

**Erros.** Lance exceções de domínio específicas (`TicketNotFoundException`,
`ForbiddenAssignmentException`) e traduza para HTTP em um `@RestControllerAdvice`
único. Nunca monte `ResponseEntity` com status na mão dentro do controller.

A exceção **estende `common/error/DomainException` e declara um `ProblemKind`** — o
tipo de problema, não o número HTTP. O advice global traduz todas por esse tipo. **Não
acrescente `@ExceptionHandler` no advice para a exceção de um módulo:** isso obrigaria
`common` a importar o módulo, que já depende de `common` — ciclo, e o `ModularityTest`
quebra o build. O `title` e o `detail` vão no corpo da resposta: nunca ponha ali nome de
classe, mensagem de biblioteca ou o identificador que a pessoa tentou.

**Migrations.** Todo schema muda via Flyway em
`backend/src/main/resources/db/migration`, nomeado `V{n}__descricao_em_snake_case.sql`.
Nunca edite uma migration já commitada — crie a próxima. O `ddl-auto` fica em
`validate`, nunca `update`.

Dado que existe só para desenvolver não entra nessa linha: vai para
`db/seed/`, como migration **repetível** (`R__`), e é o perfil `dev` que acrescenta a
location. Repetível, e não versionada, porque um número de versão que só alguns bancos
aplicam faz a próxima migration de domínio entrar fora de ordem nos demais — e o Flyway
recusa isso por padrão. Sendo repetível, ela roda de novo a cada edição do arquivo:
tudo ali precisa ser idempotente.

**Datas e horas.** Persista tudo em UTC com `Instant`. Conversão para o fuso do usuário
é responsabilidade do frontend. A única exceção é `WorkSchedule`, que guarda horário
local mais a zona.

## Convenções — frontend

### Estrutura

A unidade de organização é o **módulo**, não a camada técnica:

```
src/
├─ app/                        ← rotas do Next (App Router)
├─ modules/
│  ├─ ticket/
│  │  ├─ components/
│  │  │  ├─ atoms/
│  │  │  ├─ molecules/
│  │  │  ├─ organisms/
│  │  │  └─ pages/
│  │  ├─ actions/              ← Server Actions do módulo
│  │  ├─ services/             ← acesso à API do módulo
│  │  ├─ hooks/
│  │  ├─ utils/
│  │  └─ types/
│  └─ shared/                  ← mesma forma interna, para o que atravessa módulos
├─ lib/                        ← infraestrutura transversal
├─ styles/                     ← tokens e estilos globais
└─ types/                      ← tipos gerados do OpenAPI
```

`components/` contém **apenas componentes**. Service, hook e util moram ao lado dele,
dentro do módulo — não dentro de `components/`.

**`shared` ou `lib`?** Componente, hook ou regra de UI reutilizada entre módulos vai
para `modules/shared/`. Infraestrutura que não pertence a módulo nenhum — cliente HTTP,
leitura de sessão, configuração — vai para `lib/`.

### Atomic design

Quatro níveis, e só quatro:

| Nível | O que é | Exemplo |
|---|---|---|
| `atoms` | Elemento indivisível, sem regra de negócio. | `StatusBadge`, `Button` |
| `molecules` | Poucos atoms compondo uma unidade com propósito. | `SlaIndicator`, `SearchField` |
| `organisms` | Bloco autônomo de tela, com estado próprio quando precisa. | `CommentThread`, `AssignmentPanel` |
| `pages` | A tela inteira composta. Recebe **tudo** por props. | `TicketDetailPage` |

**Não existe nível `templates`.** Em React ele colapsa com `pages` — o componente que
recebe os dados por props já é as duas coisas — e manter os dois produziria uma divisão
sem critério objetivo, decidida caso a caso.

### Rota e tela são coisas separadas

`app/**/page.tsx` é a **rota**: autenticação, busca dos dados, `metadata`, redirect.
O componente em `components/pages/` é a **tela**: compõe organisms e não sabe de onde
os dados vieram.

**Componente de `pages/` nunca busca dados.** Se ele fizer fetch, a separação deixa de
existir na prática — você só moveu o `await` de lugar — e a tela volta a não ser
testável isoladamente. Tudo entra por props.

### Anatomia de um componente

Cada componente é uma pasta com tudo que só ele usa:

```
molecules/SlaIndicator/
├─ SlaIndicator.tsx
├─ SlaIndicator.test.tsx
├─ styles.module.css
├─ utils.ts
└─ index.ts              ← reexporta, para o import ficar curto
```

O arquivo leva o nome do componente em vez de `index.tsx`: dez abas de editor escritas
"index.tsx" não dizem nada.

Quando um util, tipo ou hook passa a ser usado por outro componente, ele **sobe** — para
a pasta do módulo (`modules/ticket/utils/`) ou, se atravessar módulos, para
`modules/shared/`. Nada de importar arquivo interno da pasta de outro componente.

### Server e client

**Server Components por padrão.** Só marque um componente como client quando ele
precisar de estado, efeito ou handler de evento. Empurre a fronteira de cliente o mais
para baixo possível na árvore: o normal é o componente de página ser Server Component e
compor organisms client onde há interatividade.

**Cuidado com a fronteira de serialização.** O que atravessa de Server para Client
precisa ser serializável — nada de função, instância de classe ou objeto com métodos.
Datas viajam como string ISO e são formatadas do lado do cliente.

**Chamadas à API saem do servidor.** Route Handlers e Server Actions falam com o
backend; o browser não vê o token JWT. O token fica em cookie `httpOnly`.

**Server Actions ficam em `modules/{módulo}/actions/`**, com `'use server'` no topo.
Leitura (`services/`) e escrita (`actions/`) do módulo ficam lado a lado, e nenhuma das
duas suja `components/`.

### Estilo

**CSS Modules com design tokens em CSS custom properties.** São camadas distintas e
ambas são obrigatórias: CSS Modules resolve **escopo** (o estilo não vaza), os tokens
resolvem **vocabulário** (quais valores são permitidos).

Os tokens vivem em `src/styles/tokens.css` como `--color-status-open`, `--space-3`,
`--radius-sm`. Todo `styles.module.css` consome token — **valor solto é proibido**. Sem
essa regra, em três meses o sistema tem sete azuis levemente diferentes.

Nada de CSS-in-JS com runtime: styled-components e equivalentes forçam `'use client'`
em tudo que estilizam, o que derruba boa parte do ganho de Server Components.

### Tipos

**Tipos vêm do OpenAPI.** Não escreva à mão tipos que espelham DTOs do backend —
gere-os a partir do schema em `src/types/api.d.ts`. Tipo escrito à mão sai de sincronia
silenciosamente, e o TypeScript continua verde enquanto a tela quebra.

### Nomes

Pasta e arquivo de componente em `PascalCase`; todo o resto em `kebab-case.ts`. Nomes
de classe CSS em `camelCase`, porque é assim que são consumidos (`styles.slaOverdue`).

## Testes

O projeto é desenvolvido com teste primeiro — mas não em todos os níveis. A regra que
decide isso é objetiva:

> **Se a regra só existe quando o banco executa a query, o teste de integração vem
> antes da implementação. Em todo o resto: unitário antes, integração e E2E depois.**

### Quando escrever cada nível

| Nível | Quando | Escopo e motivo |
|---|---|---|
| Unitário | **Antes** — TDD estrito, red → green → refactor | Domínio e services com dependências mockadas, sem contexto Spring. É onde o loop rápido paga e onde o desenho da API do service é pressionado. |
| Integração de regra em SQL | **Antes** | `TicketSpecifications` e constraints de schema. Aqui a integração *é* o nível unitário: com repository mockado o teste só prova que o mock foi chamado, não que a regra vale. |
| Integração geral | Depois | CRUD, validação, mapeamento e camada web. Valor de regressão, não de desenho. |
| E2E | Depois | Fluxo completo no browser. A **lista de cenários** em texto, porém, vem antes de implementar: é critério de aceite, não teste. |

O motivo de a exceção existir: a regra de visibilidade mora em dois lugares —
`TicketAccessPolicy` (decide sobre um ticket já carregado) e `TicketSpecifications` (a
mesma regra em SQL, para filtrar na listagem). Se as duas divergirem, alguém lista
tickets e vê o que não deveria. A segunda só é verificável contra um Postgres real, e
por isso o teste dela não pode vir depois.

### Ferramental

| Camada | Ferramenta | Observação |
|---|---|---|
| Unitário backend | JUnit 5 + AssertJ + Mockito | AssertJ em vez das assertions do JUnit: a mensagem de falha é legível meses depois. |
| Integração backend | Spring Boot Test + Testcontainers (Postgres 16) | Postgres real, nunca H2 — o schema usa recursos específicos do Postgres. Ative `testcontainers.reuse.enable=true` para não pagar o startup a cada rodada. |
| Camada web | MockMvc | Cobre controller, serialização e o `@RestControllerAdvice` sem subir servidor. |
| Fronteira entre módulos | `ApplicationModules.verify()` em `ModularityTest` | Quebra o build quando um módulo importa classe interna de outro ou quando surge ciclo. Modularidade verificada, não combinada. |
| Módulo isolado | `@ApplicationModuleTest` | Sobe apenas um módulo em vez da aplicação inteira; bem mais rápido que `@SpringBootTest`. |
| Cobertura | JaCoCo, com threshold **apenas** em `domain` e `service` | Threshold global ensina a escrever teste falso para cobrir getter e configuração. |
| Mutation testing | PITest (`pitest-maven`) | Altera o código de propósito — inverte um `if`, troca `>` por `>=` — e falha se nenhum teste quebrar. É o antídoto contra cobertura inflada. Roda sobre `domain` e `service`. |
| Unitário frontend | Vitest + React Testing Library | Teste comportamento visível ao usuário, não implementação. |
| Mock de API no frontend | MSW | Intercepta no nível da rede, então o componente é testado sem saber que está mockado. |
| E2E | Playwright | Contra a stack completa no Docker. O auto-wait elimina flakiness de timing e o trace viewer mostra o passo a passo de uma falha sem precisar reproduzir. |
| Dados de teste | Builders (`TicketBuilder.aTicket().assignedToTeam(x).build()`) | Sem eles, o setup de um teste com ticket + equipe + SLA vira 40 linhas ilegíveis e o time para de escrever teste. |

### Regras que não se negociam

**Toda regra de visibilidade precisa de teste de integração cobrindo o caso negativo:**
alguém que *não* deveria ver o ticket recebe 403 ou 404. Essa é a parte do sistema onde
um bug é mais caro — listar tickets de outra equipe é vazamento de dados, não bug de
tela.

Teste escrito depois tende a confirmar a implementação em vez do requisito: você olha o
código que existe e escreve o teste que ele passa. Nos níveis em que o teste vem depois,
escreva a asserção a partir da regra documentada aqui, não a partir do código.

### Definition of done de uma feature

1. Teste unitário escrito antes da implementação, verde.
2. Teste de integração cobrindo o caso negativo de permissão.
3. Teste E2E do fluxo feliz, com os cenários listados antes de implementar.
4. `./mvnw verify` passando, incluindo threshold de cobertura.
5. `pnpm lint`, `pnpm typecheck`, `pnpm test` e `pnpm build` passando, se tocou no
   frontend.
6. `CODEBASE-MAP.md` atualizado.

**Não existe CI neste projeto.** Nenhum pipeline vai pegar o que passar batido, então
os passos acima são a única rede — rode todos antes de commitar, não só os do lado que
você mexeu.

## Git

Commits **em inglês**, no formato `TAG - descrição no imperativo`:

```
FEAT - add ticket transition to REOPENED
```

Tags em uso: `FEAT` (funcionalidade nova), `FIX` (correção de bug), `REFACTOR` (muda o
código sem mudar comportamento), `TEST` (só testes), `SPECS` (documentação), `REMOVE`
(remoção), `CHORE` (build, configuração, dependências).

Um commit por unidade lógica de trabalho. O corpo — também em inglês — explica **por
quê**, não o quê: o diff já mostra o que mudou, e é o motivo que se perde em seis meses.

**A mensagem tem que se explicar sozinha.** Nada de número de RFC, de norma ou de
sigla que obrigue quem lê a procurar fora do commit: escreva o que aquilo faz. "Every
API failure now returns the same shape" serve; "adopt RFC 9457" não serve. Nome de
classe ou de biblioteca que está no próprio diff pode — isso o leitor encontra ali
mesmo. (Em ADR é o contrário: lá a citação da norma é o ponto.)

Não commite `.env`, `target/`, `node_modules/` ou `.next/`.

## O que não fazer

- Não crie arquivos de documentação avulsos. Documentação de estrutura vai no
  `CODEBASE-MAP.md`; decisões de arquitetura vão em `docs/adr/`; o plano de trabalho vai
  em `docs/roadmap.md`. **Esses três são os únicos destinos.** Se algo não couber em
  nenhum, pergunte em vez de criar um arquivo novo — foi assim que o roadmap virou
  destino oficial em vez de um `TODO.md` na raiz.
- Não adicione dependência nova sem necessidade clara — cada uma é uma linha a
  justificar em uma entrevista.
- Não desabilite regra de lint para fazer o código passar; corrija o código.
- Não use `@SuppressWarnings`, `any` ou casts vazios para silenciar o compilador.
- Não implemente notificações, anexos ou dashboard: estão explicitamente fora do
  escopo atual.
