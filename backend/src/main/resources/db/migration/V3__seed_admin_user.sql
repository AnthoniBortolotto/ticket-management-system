-- O primeiro administrador. Sem ele nao existe caminho para criar ninguem mais: todo
-- endpoint de gestao de usuario exige um admin autenticado.
--
-- A SENHA NAO ESTA AQUI, e nem poderia estar. O que entra e um hash BCrypt ja pronto,
-- vindo de `${admin_password_hash}` — um placeholder do Flyway alimentado pela variavel
-- de ambiente ADMIN_PASSWORD_HASH (ver application.yml e backend/.env.example).
--
-- O QUE ACONTECE SE ADMIN_PASSWORD_HASH NAO ESTIVER DEFINIDA — verificado rodando, nao
-- deduzido: o Spring NAO reclama. O binder dele deixa `${ADMIN_PASSWORD_HASH}` como
-- texto literal, o Flyway substitui esse texto aqui e a aplicacao sobe com um admin cuja
-- senha e a string '${ADMIN_PASSWORD_HASH}'. Ninguem consegue logar, e nada avisa.
--
-- Quem transforma isso em falha alta e o CHECK users_password_hash_is_bcrypt, da V2: o
-- texto literal nao tem formato de hash, o INSERT abaixo e recusado e o boot para nesta
-- migration. Um default para essa variavel seria pior ainda — significaria um admin de
-- senha conhecida publicamente em todo ambiente que esquecesse de definir a dela.
--
-- O checksum do Flyway e calculado sobre este arquivo, nao sobre o valor substituido,
-- entao cada ambiente pode ter a sua senha sem quebrar a validacao das migrations.
--
-- Migration versionada: roda uma vez. Trocar a senha depois disto e operacao de
-- aplicacao, nunca uma migration nova.
--
-- SE ESTA MIGRATION FALHAR, nao ha estado sujo para limpar: o Postgres tem DDL e DML
-- transacionais e o Flyway envolve a migration numa transacao, entao a linha em
-- flyway_schema_history tambem volta atras. Corrija a variavel e suba de novo — nada de
-- `flyway repair`.
--
-- O nome vai literal, e nao em placeholder, de proposito: o Flyway substitui placeholder
-- por texto puro, sem escapar nada, e um apostrofo legitimo em nome proprio ("Joana
-- D'Arc") quebraria a migration por erro de sintaxe. Nome e dado de exibicao e se muda
-- pela aplicacao depois; nao vale abrir uma porta de injecao por ele. O e-mail e o hash
-- continuam vindo do ambiente porque identificam a conta e nao podem ser fixos aqui.

INSERT INTO users (email, full_name, password_hash, role, created_at, updated_at)
VALUES (lower('${admin_email}'),
        'Administrador',
        '${admin_password_hash}',
        'ADMIN',
        now(),
        now());
