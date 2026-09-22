-- O que o login precisa guardar: sessoes renovaveis e tentativas fracassadas.
--
-- As duas tabelas vivem no mesmo arquivo por serem a mesma unidade logica. Elas
-- pertencem ao modulo `auth` — sao conceitos de sessao, nao de cadastro. E por isso que
-- nenhuma das duas se chama `user_algo`: o prefixo sugeriria dono errado.
--
-- NENHUMA DAS DUAS TEM LINHA OBRIGATORIA, e o seed de demonstracao nao muda. Semear uma
-- conta ja bloqueada seria ativamente ruim: o seed e repetivel (`R__`) e `locked_until` e
-- instante absoluto, entao ou ficaria eternamente no passado, sem demonstrar nada, ou o
-- elenco se re-bloquearia sozinho a cada edicao do arquivo. Quem quiser ver o bloqueio,
-- erra a senha.


-- ---------------------------------------------------------------------------
-- login_lockouts
-- ---------------------------------------------------------------------------
--
-- POR QUE NAO SAO COLUNAS EM `users`. A `BaseEntity` marca `updated_at` com
-- @LastModifiedDate. Um contador de falhas dentro de `users` faria essa coluna significar
-- "quando alguem errou a senha desta conta" em vez de "quando este cadastro mudou" — e o
-- valor antigo ja teria sido sobrescrito, sem volta. Pior: trafego ANONIMO passaria a
-- escrever na linha de `users`, e o e-mail do admin esta publicado no README.
--
-- POR QUE A CHAVE E `user_id` E NAO O E-MAIL. Com a FK, esta tabela e limitada pelo
-- numero de contas. Chaveada por e-mail, qualquer anonimo criaria linhas para enderecos
-- inventados — crescimento sem limite dirigido por quem nao esta autenticado. Tentativa
-- contra e-mail que nao existe nao precisa de contador: nao ha conta a proteger.
--
-- Consequencia para o Java: como a PK e o proprio `user_id`, a entidade NAO herda de
-- `BaseEntity` (que traz @GeneratedValue e os carimbos de auditoria). Isto e deliberado —
-- nao "conserte" depois. `last_failed_at` e o `updated_at` desta tabela, com nome honesto.
--
-- O LIMIAR E A DURACAO DO BLOQUEIO NAO ESTAO AQUI. Ficam em @ConfigurationProperties. A
-- distincao em relacao ao SLA, que o CLAUDE.md manda guardar em banco: prazo de SLA e
-- dado de negocio que um admin edita em runtime; limiar de bloqueio e parametro
-- operacional de seguranca, que muda com deploy.
--
-- `locked_until` e instante, e nao um booleano `locked`: booleano exige alguem para
-- desliga-lo — job agendado ou intervencao — enquanto um instante expira sozinho e a
-- leitura e pura. "Esta bloqueada?" e decidido em Java, comparando com um `Clock`
-- injetado, NUNCA com now() dentro do SQL: regra em SQL nao e testavel por unitario nem
-- alcancavel pelo PITest.
--
-- A linha nasce preguicosa, na primeira falha, com
--   INSERT ... ON CONFLICT (user_id) DO UPDATE
--     SET failed_attempts = login_lockouts.failed_attempts + 1
-- Incremento relativo no SQL, e nao find+save em Java: duas falhas simultaneas com
-- find+save perdem uma contagem, e a primeira falha de duas requisicoes concorrentes
-- quebraria por violacao de chave.

CREATE TABLE login_lockouts
(
    user_id         BIGINT                   NOT NULL,
    failed_attempts INTEGER                  NOT NULL DEFAULT 0,
    last_failed_at  TIMESTAMP WITH TIME ZONE,
    locked_until    TIMESTAMP WITH TIME ZONE,
    CONSTRAINT login_lockouts_pk PRIMARY KEY (user_id),
    CONSTRAINT login_lockouts_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT login_lockouts_attempts_not_negative CHECK (failed_attempts >= 0),
    -- Conta bloqueada sem nenhuma falha registrada e estado impossivel: ou ha um caminho
    -- no service que ninguem previu, ou alguem editou o banco na mao.
    CONSTRAINT login_lockouts_lock_requires_failure
        CHECK (locked_until IS NULL OR last_failed_at IS NOT NULL)
);


-- ---------------------------------------------------------------------------
-- refresh_tokens
-- ---------------------------------------------------------------------------
--
-- O refresh token e uma string opaca aleatoria de 256 bits, NAO um JWT: um JWT se valida
-- sozinho, que e exatamente o oposto de revogavel, e ele precisaria estar em banco de
-- qualquer forma para poder ser revogado — o formato autocontido nao pagaria nada.
--
-- GUARDAMOS O HASH, NUNCA O VALOR. Um refresh token e credencial bearer de vida longa: um
-- dump de banco, um SELECT * num log ou um backup mal guardado entregaria sessoes vivas.
--
-- SHA-256 E NAO BCRYPT, e o contraste com `users.password_hash` e o ponto. BCrypt existe
-- porque senha humana tem pouca entropia e precisa de custo artificial; aqui sao 256 bits
-- de CSPRNG, nao ha o que forcar bruta. E o sal por linha do BCrypt impossibilitaria
-- `WHERE token_hash = ?` — a busca viraria varredura da tabela inteira com uma
-- verificacao por linha.
--
-- O CHECK DE FORMATO e o mesmo mecanismo da V2 com outra roupa. A falha silenciosa
-- analoga aqui e gravar o token cru por engano: tudo funciona, o teste de ponta a ponta
-- passa, e o banco virou um cofre de credenciais em texto. Com o CHECK, isso e uma
-- violacao de integridade no primeiro login. VARCHAR e nao CHAR: `bpchar` tem semantica
-- de padding e briga com o mapeamento padrao de String no `ddl-auto: validate`.
--
-- ROTACAO: cada renovacao marca `used_at` na linha antiga e insere outra com o MESMO
-- `family_id`. Nao ha UPDATE do hash — cada token e uma linha, e a linhagem e a familia.
--
-- REUSO: apresentar um token que ja tem `used_at` (ou `revoked_at`) e replay — alguem
-- guardou um token ja rotacionado. A reacao e revogar a FAMILIA inteira, com
-- `revoked_reason = 'REUSE_DETECTED'`. E o que faz ladrao e vitima cairem juntos, em vez
-- de so o mais lento dos dois perder o acesso. A alternativa mais simples — revogar todos
-- os tokens do usuario — derrubaria todos os dispositivos dele por causa de um.
--
-- `revoked_reason` nao e enfeite: e a diferenca entre "a sessao acabou" e "a sessao acabou
-- porque alguem fez replay", e e o que o teste de reuso afirma. Bem mais forte do que
-- "o proximo refresh deu 401", que passaria mesmo sem revogar familia nenhuma.
--
-- SEM @ManyToOne NO JAVA: `auth` nao pode referenciar `com.ticketsystem.user.domain.User`,
-- que e classe interna de outro modulo — o Modulith quebraria o build. A entidade guarda
-- um `Long user_id` puro. Sem associacao JPA nao existe cascata de JPA: a cascata do banco
-- abaixo e a UNICA que existe.
--
-- RETENCAO: esta tabela acumula tokens expirados e nao ha expurgo nesta fase. Mesma
-- categoria do `event_publication_archive` da V1. A consulta do expurgo, quando existir:
--   DELETE FROM refresh_tokens WHERE expires_at < now() - INTERVAL '30 days';
-- O indice sobre `expires_at` entra junto com esse job, nao antes.

CREATE TABLE refresh_tokens
(
    id             BIGINT GENERATED BY DEFAULT AS IDENTITY,
    user_id        BIGINT                   NOT NULL,
    -- SHA-256 em hexadecimal minusculo.
    token_hash     VARCHAR(64)              NOT NULL,
    -- Linhagem: todos os tokens que descendem de um mesmo login.
    family_id      UUID                     NOT NULL,
    expires_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Preenchido na rotacao. Token com `used_at` apresentado de novo e replay.
    used_at        TIMESTAMP WITH TIME ZONE,
    revoked_at     TIMESTAMP WITH TIME ZONE,
    revoked_reason VARCHAR(20),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT refresh_tokens_pk PRIMARY KEY (id),
    CONSTRAINT refresh_tokens_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    -- Unico tambem para a deteccao de reuso nao ficar ambigua: com duas linhas de mesmo
    -- hash, nao daria para saber qual delas ja foi usada.
    CONSTRAINT refresh_tokens_token_hash_unique UNIQUE (token_hash),
    CONSTRAINT refresh_tokens_token_hash_is_sha256 CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT refresh_tokens_expires_after_creation CHECK (expires_at > created_at),
    CONSTRAINT refresh_tokens_revocation_has_reason
        CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL)),
    CONSTRAINT refresh_tokens_revoked_reason_check
        CHECK (revoked_reason IN ('LOGOUT', 'PASSWORD_CHANGE', 'REUSE_DETECTED', 'ADMIN'))
);

-- Sustenta a revogacao em cascata quando um reuso e detectado.
CREATE INDEX refresh_tokens_family_idx ON refresh_tokens (family_id);

-- Sustenta "encerrar todas as sessoes" e a troca de senha. E necessario tambem porque no
-- Postgres uma FOREIGN KEY nao cria indice no lado que referencia: sem ele, apagar um
-- usuario varre esta tabela inteira.
CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (user_id);
