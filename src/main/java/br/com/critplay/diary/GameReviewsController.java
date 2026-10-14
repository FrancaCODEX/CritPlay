package br.com.critplay.diary;

import br.com.critplay.catalog.CatalogService;
import br.com.critplay.users.UserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.security.Principal;
import java.time.Instant;
import java.util.List;

@RestController
public class GameReviewsController {
    private final JdbcTemplate db;
    private final CatalogService catalog;
    private final UserService users;
    public GameReviewsController(JdbcTemplate db, CatalogService catalog, UserService users) {
        this.db = db; this.catalog = catalog; this.users = users;
    }
    public record Review(String username, String displayName, Integer rating, String review, Instant updatedAt) {}
    public record Summary(Double averageRating, long ratingCount, long reviewCount, List<Review> reviews) {}

    @GetMapping("/api/games/{gameId}/reviews")
    @Transactional(readOnly = true)
    public Summary reviews(@PathVariable Long gameId, Principal principal) {
        catalog.find(gameId);
        long viewer = principal == null ? -1 : users.require(principal.getName()).getId();
        // Use the same visibility rules for both the mean and the displayed reviews.
        String visible = """
            FROM diary_entries d JOIN app_users u ON u.id=d.user_id
            WHERE d.game_id=? AND u.enabled=TRUE AND d.review_hidden=FALSE
            AND NOT EXISTS (SELECT 1 FROM user_blocks b
                WHERE (b.blocker_id=? AND b.blocked_id=u.id) OR (b.blocked_id=? AND b.blocker_id=u.id))
            AND (u.id=? OR ((u.discoverable=TRUE OR EXISTS (SELECT 1 FROM friendships f
                WHERE f.status='ACCEPTED' AND ((f.first_id=? AND f.second_id=u.id) OR (f.second_id=? AND f.first_id=u.id))))
                AND (u.visibility='PUBLIC' OR (u.visibility='FRIENDS' AND EXISTS (SELECT 1 FROM friendships f
                WHERE f.status='ACCEPTED' AND ((f.first_id=? AND f.second_id=u.id) OR (f.second_id=? AND f.first_id=u.id)))))))
            """;
        Object[] args = {gameId, viewer, viewer, viewer, viewer, viewer, viewer, viewer};
        var stats = db.queryForMap("SELECT AVG(CAST(d.rating AS DOUBLE PRECISION)) AS mean, COUNT(d.rating) AS ratings, "
            + "COUNT(CASE WHEN TRIM(COALESCE(d.review,''))<>'' THEN 1 END) AS reviews " + visible, args);
        var reviews = db.query("SELECT u.username,u.display_name,d.rating,d.review,d.updated_at " + visible
            + " AND (d.rating IS NOT NULL OR TRIM(COALESCE(d.review,''))<>'') ORDER BY d.updated_at DESC,d.id DESC LIMIT 50",
            (rs, row) -> new Review(rs.getString("username"), rs.getString("display_name"),
                rs.getObject("rating", Integer.class), rs.getString("review"), rs.getTimestamp("updated_at").toInstant()), args);
        return new Summary(stats.get("mean") == null ? null : ((Number) stats.get("mean")).doubleValue(),
            ((Number) stats.get("ratings")).longValue(), ((Number) stats.get("reviews")).longValue(), reviews);
    }
}
