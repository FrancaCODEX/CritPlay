CREATE TABLE account_email_outbox (
 id VARCHAR(36) PRIMARY KEY,recipient VARCHAR(254) NOT NULL,username VARCHAR(30) NOT NULL,
 kind VARCHAR(30) NOT NULL,status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
 attempts INTEGER NOT NULL DEFAULT 0,created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 last_attempt_at TIMESTAMP WITH TIME ZONE,sent_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX account_email_pending ON account_email_outbox(status,created_at);
