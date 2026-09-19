# ADR 0001 — Versões da stack e as incompatibilidades que elas trazem

- **Status:** aceito
- **Data:** 2026-09-19

## Contexto

O `CLAUDE.md` fixa Java 25, Spring Boot 4.x, Next.js com App Router e pnpm, mas deixa
três compatibilidades em aberto para conferir na hora de escrever o `pom.xml`, porque
são as que costumam ficar para trás quando Java e Spring sobem de major. Ao conferir,
apareceu uma quarta que ninguém tinha previsto — e é a mais perigosa das quatro, porque
não quebra nada.

Este ADR registra o que foi verificado nos registries no dia do setup e o que cada
verificação decidiu. Ele existe para que ninguém reabra essas perguntas sem motivo — e
para que, quando reabrir, saiba o que mudou desde então.

## Decisão

### Backend

| Item | Versão | Por quê |
|---|---|---|
| Java | 25 | LTS vigente, já instalado na máquina de desenvolvimento. |
| Spring Boot | 4.1.1 | Último estável. A 4.2.0-M1 existe, mas milestone não entra em projeto que se lê como referência. |
| Spring Modulith | 2.1.1 | Linha alinhada ao Boot 4. |
| springdoc-openapi | 3.1.1 | **Bloqueante resolvido.** A linha 3.x é a que suporta Boot 4 — o POM dela compila contra 4.1.0. Sem isso não há schema OpenAPI, e sem schema o frontend não tem tipos. |
| Flyway | 12.4.0 (gerenciado pelo Boot) | — |
| Testcontainers | 2.0.5 (gerenciado pelo Boot) | — |
| JaCoCo | 0.8.15 | Conhece o bytecode do Java 25. Verificado: `./mvnw verify` roda e aplica o threshold. |
| PITest | 1.30.0 + plugin JUnit 5 1.2.3 | **Verificado em Java 25**: gera e mata mutações normalmente. Precisou de `parseSurefireConfig=false`, porque o PITest copia o `argLine` do surefire e não resolve a substituição tardia `@{...}`. |

### Quatro armadilhas confirmadas na prática

**1. O Boot 4 usa Jackson 3, não Jackson 2.** O BOM traz `tools.jackson` 3.1.5 como
padrão e mantém `com.fasterxml.jackson` 2.21.5 apenas como fallback. Consequência
direta: qualquer configuração de serialização escrita com `com.fasterxml.jackson.*`
compila (a classe está no classpath) e **não tem efeito nenhum**, porque não é esse o
`ObjectMapper` que o Boot usa.

Pior: a flag `WRITE_DATES_AS_TIMESTAMPS` saiu de `SerializationFeature` e foi para
`tools.jackson.databind.cfg.DateTimeFeature`, e o Boot 4 **não expõe propriedade** para
esse grupo. Escrever `spring.jackson.serialization.write-dates-as-timestamps: false`,
que é o que funcionava no Boot 3, derruba o contexto no boot com
`No enum constant ... write-dates-as-timestamps`.

Por isso `config/JacksonConfig.java` existe como classe: ele registra um
`JsonMapperBuilderCustomizer` que desliga a flag no enum certo. E como o formato da data
é contrato publicado, `JacksonConfigTest` fixa a saída ISO-8601 em vez de confiar no
default da biblioteca.

Isso respinga na escolha futura da biblioteca de JWT: o `jjwt-jackson` depende de
Jackson 2 e não existe variante para Jackson 3. Ao implementar o módulo `auth`, a
decisão é entre `jjwt-gson`, conviver com os dois Jacksons no classpath, ou outra
biblioteca. Nenhuma dependência de JWT foi adicionada ainda, justamente para não
decidir isso sem necessidade.

**2. `spring-boot-starter-web` está deprecado no Boot 4**, em favor de
`spring-boot-starter-webmvc`. O antigo ainda resolve, então o build não avisa. Usamos o
novo.

**2b. As autoconfigurações saíram do `spring-boot-autoconfigure` para módulos por
tecnologia** (`spring-boot-flyway`, `spring-boot-jackson`, `spring-boot-jdbc`…). A
consequência é a pior categoria de falha: com apenas `org.flywaydb:flyway-core` no
classpath — que é o que se escreve por hábito vindo do Boot 3 — a aplicação sobe,
responde, **não emite uma linha de log de Flyway e não aplica migration nenhuma**. Nada
falha; o schema simplesmente nunca é criado.

Isso foi descoberto no setup e agora tem teste:
`TicketSystemApplicationIT.oFlywayRodaEDeixaARastreabilidadeDoSchema` verifica que a
tabela `flyway_schema_history` existe. A regra que sobra: ao adicionar uma tecnologia,
use o starter do Boot, não a biblioteca crua, e **confirme no log que ela inicializou**.

**3. O Testcontainers 2.0 renomeou os módulos:** `org.testcontainers:postgresql` virou
`org.testcontainers:testcontainers-postgresql`, e o mesmo para `junit-jupiter`. Os
nomes antigos param na 1.21.x, e como o BOM do Boot não os gerencia mais, o erro que
aparece é "version is missing" — que não se parece em nada com a causa.

### Frontend

| Item | Versão | Por quê |
|---|---|---|
| Node | 24 | A versão da máquina; fixada em `.nvmrc` e em `engines`. |
| Next.js | 16.3.5 | Última estável, App Router. |
| React | 19.3.0 | Exigida pelo Next 16. |
| **TypeScript** | **5.9.3, e não a 7.0.2** | Ver abaixo. |
| ESLint | 9.39.5 | Ver abaixo. |
| Vitest | 5.0.1 | — |
| Playwright | 1.63.0 | — |

**TypeScript fica na 5.9.3 mesmo com a 7.0.2 disponível.** O `eslint-config-next@16.3.5`
depende de `typescript-eslint@^8.46`, que declara peer `typescript >=4.8.4 <6.1.0`. Com
a 7.0.2 o lint entra em conflito de peer e as regras que dependem de tipo podem parar de
funcionar — e o `CLAUDE.md` proíbe desabilitar regra de lint para fazer o código passar.
Perde-se a velocidade do compilador nativo; ganha-se um `pnpm lint` que funciona.

A subida para a 7.x é um bump de uma linha assim que o `typescript-eslint` publicar
suporte. Vale reavaliar a cada atualização do `eslint-config-next`.

**ESLint fica na 9.39.5** e não na 10.x: o `eslint-config-next@16.3.5` declara peer
`>=9.0.0`, mas os plugins que ele arrasta (react, import, jsx-a11y) ainda não anunciam
suporte à 10. Mesmo raciocínio do TypeScript — segue a versão que o ecossistema
suporta, não a maior que existe.

## Consequências

- Duas versões ficam deliberadamente atrás do `latest` (TypeScript e ESLint). Isso é
  visível em `pnpm install`, que avisa que há versão mais nova. É intencional, e este
  ADR é a resposta a "por que não está na última?".
- A escolha da biblioteca de JWT fica em aberto até o módulo `auth`, e já se sabe que
  ela esbarra no Jackson 3.
- Nenhuma dessas versões deve ser trocada sem rodar `./mvnw verify` e
  `pnpm lint && pnpm typecheck && pnpm test`.
