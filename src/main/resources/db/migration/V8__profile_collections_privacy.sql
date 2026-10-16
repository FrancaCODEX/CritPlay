-- Preserve existing collection privacy; new accounts opt into public collections by default.
ALTER TABLE app_users ADD COLUMN library_public BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_users ADD COLUMN favorites_public BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_users ADD COLUMN wishlist_public BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_users ADD COLUMN lists_public BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_users ALTER COLUMN library_public SET DEFAULT TRUE;
ALTER TABLE app_users ALTER COLUMN favorites_public SET DEFAULT TRUE;
ALTER TABLE app_users ALTER COLUMN wishlist_public SET DEFAULT TRUE;
ALTER TABLE app_users ALTER COLUMN lists_public SET DEFAULT TRUE;
ALTER TABLE app_users ALTER COLUMN visibility SET DEFAULT 'PUBLIC';
