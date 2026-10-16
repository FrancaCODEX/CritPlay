-- Existing fixture content is updated only on explicit demo activation, never by this migration.
INSERT INTO demo_seed_runs(name,completed) VALUES ('community-variety-v2',FALSE);
