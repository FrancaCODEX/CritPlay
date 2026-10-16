-- Fixture is opt-in; migration creates no users or reviews.
CREATE TABLE demo_seed_runs (
 name VARCHAR(80) PRIMARY KEY,
 completed BOOLEAN NOT NULL DEFAULT FALSE
);
INSERT INTO demo_seed_runs(name,completed) VALUES ('community-v1',FALSE);
