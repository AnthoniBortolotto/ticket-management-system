-- Elenco de demonstracao: duas equipes, agentes, lideres e solicitantes.
--
-- SO RODA NO PERFIL dev. Esta location entra pelo `spring.flyway.locations` de
-- application-dev.yml; em test e em producao a lista fica so com db/migration. Os testes
-- de visibilidade afirmam o que alguem NAO enxerga, entao uma linha que o teste nao
-- criou pode fazer um deles passar ou falhar por acidente.
--
-- REPETIVEL (R__), nao versionada, e a razao importa: uma migration versionada aqui
-- ocuparia um numero que os bancos sem esta location nunca aplicam, e a proxima
-- migration de dominio entraria "fora de ordem" nos bancos de dev, que o Flyway recusa
-- por padrao. Repetivel roda depois de todas as versionadas e fica fora dessa ordenacao.
--
-- Repetivel tambem significa "roda de novo a cada mudanca deste arquivo", entao tudo
-- aqui e idempotente via ON CONFLICT DO NOTHING. Editar o elenco nao duplica ninguem.
--
-- A senha vem de `${demo_password_hash}`, pelo mesmo caminho do admin. Nenhum hash
-- literal em SQL, nem em arquivo que so roda em dev.

INSERT INTO teams (name, description, created_at, updated_at)
VALUES ('Suporte N1', 'Primeiro atendimento: triagem e chamados de uso do dia a dia.', now(), now()),
       ('Infraestrutura', 'Rede, servidores e acessos.', now(), now())
ON CONFLICT (lower(name)) DO NOTHING;

INSERT INTO users (email, full_name, password_hash, role, created_at, updated_at)
VALUES ('lead.suporte@ticketsystem.local', 'Carla Nunes', '${demo_password_hash}', 'AGENT', now(), now()),
       ('agente.suporte@ticketsystem.local', 'Diego Prado', '${demo_password_hash}', 'AGENT', now(), now()),
       ('lead.infra@ticketsystem.local', 'Helena Sa', '${demo_password_hash}', 'AGENT', now(), now()),
       ('agente.infra@ticketsystem.local', 'Igor Bastos', '${demo_password_hash}', 'AGENT', now(), now()),
       -- Participa das duas equipes: e o caso que prova, em dados, que o vinculo e
       -- muitos-para-muitos. Sem alguem assim, a decisao so existe no schema.
       ('agente.polivalente@ticketsystem.local', 'Marina Alves', '${demo_password_hash}', 'AGENT', now(), now()),
       ('ana.solicitante@ticketsystem.local', 'Ana Ribeiro', '${demo_password_hash}', 'REQUESTER', now(), now()),
       ('bruno.solicitante@ticketsystem.local', 'Bruno Teixeira', '${demo_password_hash}', 'REQUESTER', now(), now())
ON CONFLICT (email) DO NOTHING;

-- O ON CONFLICT de users aponta para a coluna, e o de teams para lower(name): sao
-- constraints diferentes de proposito. users.email e sempre minusculo (ha um CHECK), e
-- um UNIQUE comum na coluna deixa o login usar o indice; o nome da equipe guarda a caixa
-- digitada, entao a unicidade dele vive num indice funcional.
--
-- Vinculos montados por e-mail e nome, nao por id: ids de identity nao sao estaveis
-- entre bancos, e este arquivo roda em qualquer base de dev.
INSERT INTO team_memberships (user_id, team_id, role, created_at, updated_at)
SELECT u.id, t.id, v.role, now(), now()
FROM (VALUES ('lead.suporte@ticketsystem.local', 'Suporte N1', 'LEAD'),
             ('agente.suporte@ticketsystem.local', 'Suporte N1', 'MEMBER'),
             ('lead.infra@ticketsystem.local', 'Infraestrutura', 'LEAD'),
             ('agente.infra@ticketsystem.local', 'Infraestrutura', 'MEMBER'),
             ('agente.polivalente@ticketsystem.local', 'Suporte N1', 'MEMBER'),
             ('agente.polivalente@ticketsystem.local', 'Infraestrutura', 'MEMBER'))
         AS v(email, team, role)
         JOIN users u ON lower(u.email) = lower(v.email)
         JOIN teams t ON lower(t.name) = lower(v.team)
ON CONFLICT (user_id, team_id) DO NOTHING;
