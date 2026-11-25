-- Inventario privado para psql. Nao contem credenciais e nao altera dados.
-- Executar com acesso autorizado, fora do pico, e guardar a saida fora do Git.
-- Contagens exatas podem custar recursos; cada consulta tem limite de 60s.
\set ON_ERROR_STOP on
\pset pager off

BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '60s';
SET LOCAL lock_timeout = '5s';

SELECT current_timestamp AS snapshot_started_at,
       current_setting('server_version') AS postgres_version,
       pg_database_size(current_database()) AS database_bytes,
       pg_size_pretty(pg_database_size(current_database())) AS database_size;

SELECT extname, extversion FROM pg_extension ORDER BY extname;

SELECT schemaname, relname AS table_name, n_live_tup AS estimated_rows,
       pg_total_relation_size(relid) AS total_bytes,
       pg_size_pretty(pg_total_relation_size(relid)) AS total_size
FROM pg_stat_user_tables
ORDER BY total_bytes DESC;

-- Gera apenas SELECTs de contagem, sem retornar conteudo de usuarios/mensagens.
SELECT format('SELECT %L AS table_name, count(*) AS exact_rows FROM %I.%I;',
              schemaname || '.' || tablename, schemaname, tablename)
FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename
\gexec

SELECT format('SELECT installed_rank, version, description, script, checksum, success FROM %I.flyway_schema_history ORDER BY installed_rank;', 'public')
WHERE to_regclass('public.flyway_schema_history') IS NOT NULL
\gexec

SELECT schemaname, sequencename, last_value, increment_by, cycle
FROM pg_sequences WHERE schemaname = 'public' ORDER BY sequencename;

-- BYTEA tambem ocupa a cota do PostgreSQL. Nao retorna imagens ou filenames.
SELECT 'SELECT count(*) AS image_rows, coalesce(sum(octet_length(content)),0) AS image_payload_bytes FROM public.appearance_blobs;'
WHERE to_regclass('public.appearance_blobs') IS NOT NULL
\gexec

SELECT state, count(*) AS connections
FROM pg_stat_activity WHERE datname = current_database()
GROUP BY state ORDER BY state;

COMMIT;
