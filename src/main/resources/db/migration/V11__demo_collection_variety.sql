-- Personalization is opt-in at startup, not applied by the migration itself.
INSERT INTO demo_seed_runs(name,completed) VALUES ('community-collections-v3',FALSE);
