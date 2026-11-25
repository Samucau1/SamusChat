-- V9 is reserved for the friends/persistent-appearance migration in the deployment branch.
ALTER TABLE servers ADD COLUMN invite_code VARCHAR(3);
CREATE UNIQUE INDEX uq_servers_invite_code ON servers(invite_code);
CREATE TABLE server_invite_code_lock (id INTEGER PRIMARY KEY);
INSERT INTO server_invite_code_lock(id) VALUES (1);
-- Existing servers receive their code when an administrator next loads the server.
