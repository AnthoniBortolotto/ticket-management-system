-- Registro de publicacao de eventos do Spring Modulith.
--
-- O Modulith grava aqui, na MESMA transacao que salvou a entidade, todo evento
-- publicado entre modulos. Se o listener falhar ou a aplicacao cair no meio, a linha
-- fica sem completion_date e e reprocessada no proximo start. E isso que torna
-- `ticket` -> `sla` e `ticket` -> `audit` confiaveis sem message broker.
--
-- Estas tabelas sao do framework, nao do dominio: o formato e ditado pelo Modulith e
-- copiado do schema oficial (spring-modulith-events-jdbc, schemas/v2/postgresql).
-- Nao altere colunas aqui por conta propria; ao subir a versao do Modulith, compare
-- com o schema dele. O `ddl-auto: validate` avisa se divergirem.
--
-- Modo de conclusao: ARCHIVE. O evento concluido sai da tabela ativa e vai para o
-- arquivo, entao a consulta de pendentes no boot varre so o que de fato esta pendente,
-- e nao o historico inteiro.
--
-- ATENCAO A RETENCAO: serialized_event guarda o evento inteiro em JSON, e o arquivo
-- cresce sem limite. Quando um evento passar a carregar dado pessoal (nome de
-- solicitante, e-mail), isto vira armazenamento indefinido desse dado fora das tabelas
-- de dominio — problema de LGPD, nao de disco. Antes disso, defina expurgo do arquivo
-- ou mantenha nos eventos apenas identificadores, nunca os dados em si.

CREATE TABLE event_publication
(
    id                     UUID                     NOT NULL,
    listener_id            TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);

-- Hash, e nao B-tree: serialized_event e um JSON que pode passar do limite de tamanho
-- de indice B-tree do Postgres, e a unica consulta sobre ele e por igualdade.
CREATE INDEX event_publication_serialized_event_hash_idx
    ON event_publication USING hash (serialized_event);

-- Sustenta a varredura de pendentes no start da aplicacao.
CREATE INDEX event_publication_by_completion_date_idx
    ON event_publication (completion_date);

CREATE TABLE event_publication_archive
(
    id                     UUID                     NOT NULL,
    listener_id            TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);

CREATE INDEX event_publication_archive_serialized_event_hash_idx
    ON event_publication_archive USING hash (serialized_event);

CREATE INDEX event_publication_archive_by_completion_date_idx
    ON event_publication_archive (completion_date);
