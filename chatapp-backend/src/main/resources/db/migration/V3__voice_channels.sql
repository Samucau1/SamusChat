ALTER TABLE call_sessions ADD COLUMN channel_id BIGINT REFERENCES channels(id);
CREATE TABLE voice_presences (
    email VARCHAR(255) PRIMARY KEY,
    channel_id BIGINT NOT NULL REFERENCES channels(id),
    seen_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_voice_presences_channel ON voice_presences(channel_id);
CREATE INDEX idx_call_sessions_channel ON call_sessions(channel_id);
