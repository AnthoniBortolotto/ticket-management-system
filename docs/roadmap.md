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
| Autenticação | JWT HS256 só com `sub` e `role`; refresh opaco em banco, com rotação; bloqueio por conta | [ADR 0003](adr/0003-autenticacao-jwt.md) |
| Formato de erro por módulo | Exceção estende `DomainException` com um `ProblemKind`; nenhum módulo registra `@ExceptionHandler` próprio | `common/error/` |
| Primeiro usuário | Migration de seed, com dados de demonstração em location separada, só no perfil `dev` | `V3__seed_admin_user.sql` |
| Vínculo com equipe | Um usuário participa de várias equipes; UNIQUE no par | `V2__create_users_and_teams.sql` |
| Gestão de equipe | Criar equipe e mexer em liderança: só admin. `MEMBER`: admin ou `LEAD` da equipe. Não-membro recebe 404, membro sem permissão recebe 403 | `team/service/TeamService.java` |
| Quem entra em equipe | Agente e admin; solicitante nunca | `team/service/TeamService.java` |
| Equipe de um ticket novo | A categoria decide, por rota configurada por admin; categoria sem rota recusa a abertura com 409, sem equipe default | `V5__create_tickets_comments_and_routes.sql` |
| Fluxo de status | O diagrama do README mais `IN_PROGRESS → RESOLVED` | `ticket/TicketStatus.java` |
| O que o solicitante decide | Confirmar o fechamento (`RESOLVED → CLOSED`) e reabrir; o resto do fluxo é de quem atende | `ticket/service/TicketAccessPolicy.java` |
| Ticket invisível | 404 com o mesmo corpo de um inexistente; 403 só para quem vê e não pode | `ticket/service/TicketService.java` |
| Modo exclusivo | Líder da equipe ou admin manda, e só para quem participa da equipe. Veem e atuam o responsável e o líder da origem; a equipe perde o acesso | `ticket/service/TicketAccessPolicy.java` |
| Transferência entre equipes | Líder da equipe atual ou admin; o responsável atual não vai junto | `ticket/service/TicketAssignmentService.java` |
| Responsável atual | Qualquer membro da equipe designa qualquer membro; sair da equipe o limpa, por evento | `ticket/service/TicketAssignmentService.java` |
| Formato de página | `PageResponse`: `content`, `page` a partir de zero, `size` (20, máximo 100), `totalElements`, `totalPages` | `common/web/PageResponse.java` |
| Infra local | Compose desenvolve, Kubernetes demonstra | [ADR 0002](adr/0002-infra-local-compose-e-kubernetes.md) |
| Versões da stack | Ver a tabela e as armadilhas confirmadas | [ADR 0001](adr/0001-versoes-da-stack.md) |

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
- **Listagem de tickets:** 20 por página, ordenada por prioridade e depois por prazo de SLA
  mais próximo. Até o SLA existir, o segundo critério é o mais antigo primeiro — a Fase 6
  troca, sem mudar o formato da resposta.
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

**Concluída.** O que ela entregou está no [CODEBASE-MAP](../CODEBASE-MAP.md): as
migrations `V2` e `V3`, a location de seed `db/seed/` e o `UsersAndTeamsSchemaIT`.

Três decisões da fase mudaram o que estava escrito aqui:

- **O seed de demonstração roda só em `dev`, não em `test`.** Os testes de visibilidade
  afirmam o que alguém **não** enxerga; uma linha semeada que o teste não criou pode
  fazer um deles passar por acidente. Em teste, cada teste monta o próprio cenário.
- **Os builders subiram para as Fases 2 e 3.** `UserBuilder` e `TeamBuilder` montam
  entidades, e `User` e `Team` só existem a partir da Fase 2 — um builder escrito antes
  disso só saberia inserir SQL cru e seria reescrito. No lugar deles, a fase entregou o
  teste de schema, que é o nível que o CLAUDE.md manda escrever antes da implementação.
- **Um usuário participa de várias equipes.** O UNIQUE é sobre o par
  `(user_id, team_id)`. É o que justifica existir uma tabela de vínculo em vez de uma
  coluna em `users`, e faz a visibilidade falar em "as equipes do usuário", no plural.

Uma armadilha confirmada rodando, que vale para qualquer placeholder de Flyway daqui
para frente: **variável de ambiente ausente não gera erro no Spring.** O binder deixa
`${VARIAVEL}` como texto literal e o Flyway grava esse texto no banco. Quem transforma
isso em falha de boot é o CHECK `users_password_hash_is_bcrypt`, na `V2`. Configuração
que pode não ser lida precisa de algo que prove que ela foi.

## Fase 2 — `user` e `auth`

**Concluída.** O que ela entregou está no [CODEBASE-MAP](../CODEBASE-MAP.md) e as decisões
de autenticação no [ADR 0003](adr/0003-autenticacao-jwt.md).

O que mudou em relação ao que estava escrito aqui:

- **O token não carrega as equipes**, ao contrário do que este item previa. O conteúdo de
  um JWT só muda quando ele expira: com as equipes no token, tirar alguém de uma equipe
  continuaria dando acesso aos tickets dela até o vencimento. As equipes são resolvidas
  por requisição, via `TeamFacade` — **a Fase 5 depende disto e não pode ler equipe do
  token**.
- **`UserRole` mora na raiz de `user/`, e não em `user/domain/`.** `auth` precisa do tipo
  para montar o claim, e tipo em subpacote é interno: o `ModularityTest` quebraria o build.
- **Força bruta:** bloqueio por conta, persistido, com 423. **Swagger:** continua público,
  por decisão registrada no ADR e fixada em teste.
- **`AuthFacade` e `CurrentUser` foram para a Fase 3.** Nenhum código desta fase os usa;
  entram com o primeiro consumidor, em vez de nascerem sem uso e sem teste.
- **Erro de módulo:** exceções de domínio estendem `DomainException` e declaram um
  `ProblemKind`. O jeito anterior — cada módulo registrar `@ExceptionHandler` no advice
  global — faria `common` importar os módulos, que já dependem de `common`: ciclo, e o
  `ModularityTest` quebra o build.

Armadilhas confirmadas rodando, que valem daqui para frente:

- **Duas proteções de segurança dependem de transação e quebram em silêncio.** A falha
  contada no login e a revogação num replay acontecem logo antes de uma exceção; numa
  transação só, o rollback desfaria as duas com a suíte verde. Por isso o `AuthService`
  não tem `@Transactional` — não acrescente.
- **O BCrypt limita a senha a 72 _bytes_**, e o encoder lança exceção acima disso. Um
  `@Size` conta caracteres: 40 letras acentuadas passariam e dariam 500.

## Fase 3 — `team`

**Concluída.** O que ela entregou está no [CODEBASE-MAP](../CODEBASE-MAP.md): o módulo
`team` inteiro, `AuthFacade` e `CurrentUser`, o `TeamBuilder` e os testes do caso negativo
pelo endpoint.

Decisões da fase, que não estavam escritas aqui:

- **Quem gerencia o quê.** Adicionar e remover `MEMBER` cabe ao admin ou ao `LEAD`
  *daquela* equipe. Tudo que cria ou desfaz liderança — promover, rebaixar, remover um
  líder, inclusive a si mesmo — é só de admin: o líder enxerga os tickets que saíram da
  equipe para o modo exclusivo, e conceder isso não é decisão de um par. Criar equipe
  também é só de admin. **Afrouxar depois é barato; apertar depois quebra quem já usa.**
- **404 para quem está de fora, 403 para quem está dentro.** Quem não é admin nem
  participa da equipe recebe, em tudo sobre ela, o mesmo corpo de uma equipe inexistente.
  Um 403 confirmaria que o id existe. É o mesmo raciocínio que a visibilidade de tickets
  vai seguir na Fase 5.
- **Solicitante não entra em equipe.** Membro enxerga comentário interno, e solicitante
  nunca pode. Admin pode ser membro: ele já vê tudo, e o vínculo não lhe dá nada novo.
- **A regra de equipe mora inteira no `TeamService`**, e não parte no `SecurityConfig`.
  Por URL só dá para decidir pelo papel global; "lidera esta equipe?" depende do banco.
- **O ator é parâmetro do service.** O controller o obtém da `AuthFacade`; o service não
  lê o contexto de segurança. É o que deixa a regra testável sem Spring — a Fase 5 deve
  seguir o mesmo formato no `TicketService`.
- **`CurrentUser` lê o papel das authorities, e não do claim cru.** São as mesmas que o
  `hasRole` enxerga, então a regra por URL e a do service nunca discordam sobre o papel.
- **A `TeamFacade` só tem as perguntas pontuais.** "De quais equipes esta pessoa
  participa?" e "quais lidera?", que o filtro de listagem vai precisar, entram na Fase 5,
  com o primeiro consumidor.
- **Sem `GET /api/v1/teams`**, pelo mesmo motivo da listagem de usuários: devolver lista
  agora e paginar depois mudaria o formato da resposta, e isso já seria uma `v2`.
- **O vínculo na resposta traz só `userId` e `role`.** Nome e e-mail exigiriam uma consulta
  a `user` por membro; entram quando a tela da Fase 8 precisar, com consulta em lote.
  Campo novo não quebra contrato.

## Fase 4 — `ticket`: núcleo

**Concluída.** O que ela entregou está no [CODEBASE-MAP](../CODEBASE-MAP.md): a `V5` com o
`TicketsSchemaIT` escrito antes dela, o fluxo de status testado par a par, abertura roteada
pela categoria, transições com evento, conversa com notas internas, o contrato do
repositório e os endpoints com os casos negativos.

O que mudou em relação ao que estava escrito aqui:

- **A regra de acesso de um ticket carregado veio para esta fase.** O `TicketAccessPolicy`
  era da Fase 5, mas os endpoints desta fase sem ele seriam o vazamento que o sistema existe
  para impedir, por uma fase inteira. Ele cobre o que já existe — admin, solicitante e
  membro da equipe — e **falha fechado no modo exclusivo**: até a Fase 5, um ticket nesse
  modo só é visível para admin e solicitante.
- **Não há listagem de tickets.** Ela precisa do filtro em SQL, que é escrito teste
  primeiro na Fase 5. Por isso a decisão de formato de paginação também foi para lá — e as
  listagens de usuários e equipes esperam por ela.
- **Nota interna nunca chega ao solicitante — já testado pelo endpoint**, item que estava
  na Fase 5. A conversa de quem não atende vem de uma consulta que exclui as notas no SQL.
- **Os comentários entraram na `V5`**, com os tickets, e não na `V6` com a auditoria.
  Junto veio `ticket_routes`, o roteamento de categoria para equipe.
- **`TicketStatus` mora na raiz de `ticket/`, e não em `domain/`**, pelo mesmo motivo do
  `UserRole`: viaja no `TicketStatusChanged`, que `sla` e `audit` vão consumir. Como os
  filtros do JaCoCo e do PITest só olham `domain` e `service`, ele foi incluído à mão nos
  dois. `TicketPriority` fica em `domain` até a Fase 6 precisar dela fora.
- **`@Version` no ticket.** Duas transições simultâneas a partir do mesmo status passariam
  as duas pela validação; a segunda recebe 409. O contrato do repositório exige isso de
  todo adaptador, e o advice traduz a recusa.
- **`internal` é obrigatório no comentário**, sem default. Com default `false`, a nota
  interna de quem esqueceu o campo iria para o cliente.
- **Só `TicketStatusChanged` é publicado.** Abrir um ticket não publica evento ainda: a
  Fase 6 decide se `sla` e `audit` precisam de um `TicketOpened` ou se leem a abertura
  pela fachada.

## Fase 5 — Visibilidade e atribuição

**A parte mais cara de errar do sistema inteiro.** Listar ticket de outra equipe é
vazamento de dados, não bug de tela.

**Concluída.** O que ela entregou está no [CODEBASE-MAP](../CODEBASE-MAP.md): a regra de
visibilidade em SQL e no ticket carregado, comparadas caso a caso; a listagem paginada; as
cinco operações de atribuição com `TicketAssignmentChanged`; e a limpeza do responsável
atual quando alguém sai da equipe.

Como foi feito e o que mudou em relação ao que estava escrito aqui:

- **O `TicketSpecificationsIT` foi visto falhar antes do filtro existir.** A listagem subiu
  primeiro sem filtro nenhum, o teste falhou em 7 de 8 casos mostrando os tickets vazados,
  e só então a regra foi escrita. O oitavo caso — a comparação, pessoa por pessoa e ticket
  por ticket, entre a listagem e o `TicketAccessPolicy` — pegou a divergência seguinte: o
  SQL já seguia a regra completa e a política do ticket carregado ainda falhava fechada no
  modo exclusivo.
- **Sem `JpaSpecificationExecutor`.** A ordem por prioridade não é coluna — `URGENT` vem
  antes de `LOW`, e a coluna guarda o nome —, e o `Sort` do Spring Data só ordena por
  propriedade. A listagem monta a consulta em Criteria no adaptador, com a mesma
  `Specification` na página e na contagem, e a urgência sai da ordem do enum.
- **As equipes chegam ao SQL como conjuntos lidos na hora pela `TeamFacade`**, e não por
  subconsulta nas tabelas de `team`: a fronteira entre módulos vale no SQL também.
- **O líder da equipe de origem atua no ticket exclusivo**, além de ver e devolver —
  decisão desta fase. Nem ele nem o responsável despacham de novo: para mudar o ticket de
  lugar, ele volta para a equipe antes.
- **Responsável exclusivo não perde o ticket ao sair da equipe de origem.** O acesso dele
  vem da atribuição, não do vínculo. Só o responsável *atual*, que é conceito do modo
  equipe, é limpo.
- **A reentrega de eventos no start estava desligada.** O Modulith traz
  `republish-outstanding-events-on-restart` como `false`, e o CLAUDE.md prometia o
  contrário. Ligada nesta fase, com o primeiro listener de verdade.
- **O `createdAt` agora é truncado em microssegundos** na auditoria, e o `POST` e o `GET`
  devolvem o mesmo valor. Era o risco anotado na Fase 4.

## Fase 6 — `sla` e `audit`

Os dois escutam eventos. `ticket` não sabe que eles existem, e é isso que faz notificação
ser só mais um listener no futuro.

- [ ] `V6__create_audit_events.sql`, `V7__create_sla.sql`. Os comentários já estão na `V5`.
- [ ] `audit` escuta `TicketStatusChanged` **e** `TicketAssignmentChanged` — o segundo já é
      publicado, com o antes e o depois inteiros.
- [ ] Trocar o segundo critério da listagem, "mais antigo", por "prazo de SLA mais próximo",
      como a premissa pede. `TicketPriority` sobe para a raiz de `ticket/` quando `sla`
      precisar dela.
- [ ] Decidir como `sla` e `audit` sabem que um ticket foi aberto: um evento
      `TicketOpened`, ou a abertura lida pela fachada. Hoje só a transição publica evento.
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
- [ ] `modules/team/`: tela de equipes e membros. Precisa de `GET /api/v1/teams`, no formato
      `PageResponse` — e a de usuários, de `GET /api/v1/users`.
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
  `TicketSpecifications`). Divergência entre elas é vazamento de dados. O
  `TicketSpecificationsIT` compara as duas caso a caso, mas só no elenco que ele monta:
  uma regra nova precisa de uma pessoa e de um ticket novos lá, ou a comparação não a vê.
- **Se o listener de saída de equipe falhar**, o nome da pessoa continua como responsável
  atual até a reentrega no próximo start. A visibilidade não é afetada — ela vem do vínculo,
  que já foi apagado —, mas a tela mostraria um responsável que não enxerga o ticket.
- **Não há CI.** A disciplina de rodar tudo antes de commitar é a única proteção.
- **A resposta de criação traz o carimbo de tempo com mais precisão do que o banco guarda.**
  A auditoria do `BaseEntity` preenche `createdAt` com `Instant.now()`, que no Windows tem
  sete casas decimais; o Postgres guarda seis. O `POST` devolve o valor de memória e o
  `GET` seguinte, o gravado — o mesmo ticket com dois `createdAt` diferentes. Visto rodando
  a Fase 4; vale para toda entidade. Truncar em microssegundos no `DateTimeProvider` do
  `JpaConfig` resolve de uma vez, e precisa ser feito antes de o frontend comparar datas.
- **Duas requisições simultâneas criando a mesma equipe, ou o mesmo vínculo, dão 500.** A
  checagem prévia devolve 409, mas entre ela e o `INSERT` a outra pode gravar, e aí quem
  recusa é o índice único — sem tradução para 409. O mesmo vale para e-mail de usuário.
  O dado nunca fica inconsistente; só a resposta sai errada. Traduzir violação de
  constraint no advice resolve os três de uma vez.
- **As imagens Docker nunca foram construídas.** Podem conter erros que só aparecem na
  primeira tentativa real.
- **Bloqueio por conta é vetor de negação de serviço.** Quem souber o e-mail de alguém o
  mantém bloqueado errando a senha, e o e-mail do admin está no README. A janela curta,
  que vence sozinha, limita o estrago; limite por IP no ingress é o complemento. Ver
  [ADR 0003](adr/0003-autenticacao-jwt.md).
- **`refresh_tokens` cresce sem limite** e guarda sessões expiradas. Sem expurgo ainda; a
  consulta está anotada no cabeçalho da `V4`. Decidir junto com o expurgo do arquivo de
  eventos abaixo.
- **`event_publication_archive` cresce sem limite e guarda o evento serializado.**
  Enquanto os eventos carregarem só identificadores, é questão de disco. No dia em que
  um deles levar nome ou e-mail, vira armazenamento indefinido de dado pessoal fora das
  tabelas de domínio — decida expurgo antes disso, não depois. Está anotado no cabeçalho
  da migration.
