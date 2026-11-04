package br.com.critplay.security;

import org.springframework.jdbc.core.JdbcTemplate;

/** PostgreSQL atomically shares auth limits across autoscaled containers. */
public class DatabaseAuthRateLimitFilter extends AuthRateLimitFilter {
    private final JdbcTemplate db;
    public DatabaseAuthRateLimitFilter(boolean enabled,JdbcTemplate db){super(enabled);this.db=db;}
    @Override synchronized boolean accept(String key,int limit,long now) {
        long window=now/600_000;
        try {
            db.update("DELETE FROM auth_rate_limits WHERE window_id<?",window-1);
            return !db.queryForList("""
                INSERT INTO auth_rate_limits(client_key,window_id,requests) VALUES (?,?,1)
                ON CONFLICT(client_key) DO UPDATE SET
                requests=CASE WHEN auth_rate_limits.window_id<>EXCLUDED.window_id THEN 1 ELSE auth_rate_limits.requests+1 END,
                window_id=EXCLUDED.window_id
                WHERE auth_rate_limits.window_id<>EXCLUDED.window_id OR auth_rate_limits.requests<?
                RETURNING requests
                """,key,window,limit).isEmpty();
        } catch(org.springframework.dao.DataAccessException unavailable) {
            return false; // Fail closed during outages; never bypass throttling.
        }
    }
}
