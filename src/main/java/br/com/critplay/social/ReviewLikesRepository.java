package br.com.critplay.social;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Batch like metadata without issuing queries for every rendered review. */
@Repository
public class ReviewLikesRepository {
    public record Likes(long count, boolean likedByMe) {}
    public static final Likes NONE = new Likes(0, false);
    private final NamedParameterJdbcTemplate sql;

    public ReviewLikesRepository(JdbcTemplate db) {
        this.sql = new NamedParameterJdbcTemplate(db);
    }

    public Map<Long, Likes> summaries(Collection<Long> entries, Long viewer) {
        if (entries.isEmpty()) {
            return Map.of();
        }
        var counts = new HashMap<Long, Long>();
        sql.query("""
            SELECT l.entry_id,COUNT(*) AS likes FROM review_likes l
            JOIN app_users u ON u.id=l.user_id WHERE u.enabled=TRUE AND l.entry_id IN (:entries)
            GROUP BY l.entry_id
            """, Map.of("entries", entries), row -> {
                counts.put(row.getLong("entry_id"), row.getLong("likes"));
            });
        Set<Long> mine = viewer == null ? Set.of() : new HashSet<>(sql.queryForList(
            "SELECT entry_id FROM review_likes WHERE user_id=:viewer AND entry_id IN (:entries)",
            Map.of("entries", entries, "viewer", viewer), Long.class));
        var result = new HashMap<Long, Likes>();
        for (long entry : entries) {
            result.put(entry, new Likes(counts.getOrDefault(entry, 0L), mine.contains(entry)));
        }
        return Map.copyOf(result);
    }

    public long count(long entry) {
        return sql.queryForObject("""
            SELECT COUNT(*) FROM review_likes l JOIN app_users u ON u.id=l.user_id
            WHERE l.entry_id=:entry AND u.enabled=TRUE
            """, Map.of("entry", entry), Long.class);
    }
}
