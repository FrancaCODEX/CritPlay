CREATE TABLE auth_rate_limits (
 client_key VARCHAR(200) PRIMARY KEY, window_id BIGINT NOT NULL,
 requests INTEGER NOT NULL CHECK(requests>0)
);
CREATE INDEX auth_rate_limits_window ON auth_rate_limits(window_id);
