ALTER TABLE app_users ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE';
ALTER TABLE app_users ADD COLUMN discoverable BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_users ADD CONSTRAINT profile_visibility_check CHECK (visibility IN ('PUBLIC','FRIENDS','PRIVATE'));
CREATE TABLE user_follows (
 follower_id BIGINT NOT NULL REFERENCES app_users(id),
 followed_id BIGINT NOT NULL REFERENCES app_users(id),
 PRIMARY KEY (follower_id, followed_id), CHECK (follower_id <> followed_id)
);
CREATE INDEX follows_target_idx ON user_follows(followed_id);
CREATE TABLE friendships (
 first_id BIGINT NOT NULL REFERENCES app_users(id),
 second_id BIGINT NOT NULL REFERENCES app_users(id),
 requester_id BIGINT NOT NULL REFERENCES app_users(id),
 status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','ACCEPTED')),
 PRIMARY KEY (first_id, second_id), CHECK (first_id < second_id),
 CHECK (requester_id = first_id OR requester_id = second_id)
);
CREATE INDEX friendship_second_idx ON friendships(second_id);
CREATE TABLE game_console_sources (
 game_id BIGINT NOT NULL REFERENCES games(id),
 platform VARCHAR(20) NOT NULL CHECK (platform IN ('PLAYSTATION','XBOX','NINTENDO')),
 provider_id BIGINT NOT NULL CHECK (provider_id > 0),
 PRIMARY KEY (game_id,platform)
);
