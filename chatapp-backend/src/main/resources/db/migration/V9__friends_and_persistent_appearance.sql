CREATE TABLE friendships (
    id BIGSERIAL PRIMARY KEY,
    low_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    high_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    requested_by BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    accepted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_friend_pair UNIQUE (low_user_id, high_user_id),
    CONSTRAINT ck_friend_pair CHECK (low_user_id < high_user_id),
    CONSTRAINT ck_friend_requester CHECK (requested_by IN (low_user_id, high_user_id))
);
CREATE INDEX idx_friend_high ON friendships(high_user_id);
CREATE TABLE appearance_blobs (
    filename VARCHAR(40) PRIMARY KEY,
    content_type VARCHAR(32) NOT NULL,
    content BYTEA NOT NULL
);
