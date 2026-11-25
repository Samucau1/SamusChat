-- Additive: preserves existing users, memberships, messages and attachment URLs.
-- Legacy filesystem bytes must be backed up/imported BEFORE replacing the running container.
CREATE TABLE attachment_blobs (
    filename VARCHAR(80) PRIMARY KEY,
    content_type VARCHAR(100) NOT NULL,
    content BYTEA NOT NULL
);
