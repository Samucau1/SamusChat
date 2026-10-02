CREATE TABLE call_sessions (
    id VARCHAR(255) PRIMARY KEY,
    caller VARCHAR(255) NOT NULL,
    callee VARCHAR(255) NOT NULL,
    state VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    caller_seen TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    callee_seen TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    offer TEXT,
    answer TEXT
);
CREATE INDEX idx_call_caller_state ON call_sessions(caller,state);
CREATE INDEX idx_call_callee_state ON call_sessions(callee,state);
