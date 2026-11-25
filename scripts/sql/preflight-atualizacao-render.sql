-- Read-only psql inventory, before backup and after restoration/update.
-- Does not return names, emails, passwords, tokens or file contents.
\set ON_ERROR_STOP on
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SELECT current_database() AS database_name, current_setting('server_version') AS postgres_version,
       pg_database_size(current_database()) AS database_bytes;
SELECT version, script, checksum, success FROM public.flyway_schema_history ORDER BY installed_rank;
SELECT 'users' AS entity, count(*) AS rows FROM public.users
UNION ALL SELECT 'servers',count(*) FROM public.servers
UNION ALL SELECT 'channels',count(*) FROM public.channels
UNION ALL SELECT 'memberships',count(*) FROM public.server_members
UNION ALL SELECT 'messages',count(*) FROM public.messages
UNION ALL SELECT 'direct_messages',count(*) FROM public.direct_messages;
SELECT role, count(*) AS rows FROM public.server_members GROUP BY role ORDER BY role;
SELECT count(*) AS referenced_attachments, coalesce(sum(attachment_size),0) AS declared_bytes
FROM public.messages WHERE attachment_name IS NOT NULL;
SELECT 'SELECT count(*) AS friendships FROM public.friendships;'
WHERE to_regclass('public.friendships') IS NOT NULL
\gexec
SELECT 'SELECT count(*) AS image_blobs, coalesce(sum(octet_length(content)),0) AS image_bytes FROM public.appearance_blobs;'
WHERE to_regclass('public.appearance_blobs') IS NOT NULL
\gexec
SELECT 'SELECT count(*) AS attachment_blobs, coalesce(sum(octet_length(content)),0) AS attachment_bytes FROM public.attachment_blobs;'
WHERE to_regclass('public.attachment_blobs') IS NOT NULL
\gexec
-- A nonzero result means disk-only attachments still need a verified backup/import.
SELECT 'SELECT count(*) AS attachments_without_database_bytes FROM public.messages m WHERE m.attachment_name IS NOT NULL' ||
       CASE WHEN to_regclass('public.attachment_blobs') IS NOT NULL
         THEN ' AND NOT EXISTS (SELECT 1 FROM public.attachment_blobs b WHERE b.filename=m.attachment_name);'
         ELSE ';' END
\gexec
SELECT 'SELECT count(*) AS appearance_references_without_database_bytes FROM (' ||
       'SELECT avatar_url AS url FROM public.users UNION ALL SELECT banner_url FROM public.users UNION ALL SELECT background_url FROM public.users UNION ALL SELECT icon_url FROM public.servers UNION ALL SELECT banner_url FROM public.servers UNION ALL SELECT background_url FROM public.servers' ||
       ') refs WHERE url LIKE ''%/uploads/appearance/%''' ||
       CASE WHEN to_regclass('public.appearance_blobs') IS NOT NULL
         THEN ' AND NOT EXISTS (SELECT 1 FROM public.appearance_blobs b WHERE b.filename=regexp_replace(refs.url,''^.*/'',''''));'
         ELSE ';' END
\gexec
COMMIT;
