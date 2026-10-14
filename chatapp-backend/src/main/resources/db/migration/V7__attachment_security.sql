ALTER TABLE messages ADD COLUMN attachment_name VARCHAR(80);
ALTER TABLE messages ADD COLUMN attachment_size BIGINT;
UPDATE messages SET attachment_name = regexp_replace(attachment_url, '^.*/', '') WHERE attachment_url IS NOT NULL;
CREATE INDEX idx_messages_attachment_name ON messages(attachment_name);
