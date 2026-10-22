ALTER TABLE direct_messages ADD COLUMN read_at TIMESTAMP;
CREATE INDEX idx_direct_unread ON direct_messages(recipient_email, read_at, sender_email, id);
