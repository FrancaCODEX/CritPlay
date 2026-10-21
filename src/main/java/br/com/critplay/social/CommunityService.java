package br.com.critplay.social;

import br.com.critplay.catalog.CatalogService;
import br.com.critplay.catalog.Game;
import br.com.critplay.diary.DiaryEntry;
import br.com.critplay.diary.PlayStatus;
import br.com.critplay.shared.BadRequestException;
import br.com.critplay.shared.NotFoundException;
import br.com.critplay.users.UserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class CommunityService {
    private static final int REVIEW_LIMIT = 50;
    private static final int FEED_PAGE_SIZE = 25;
    private final JdbcTemplate db;
    private final NamedParameterJdbcTemplate sql;
    private final UserService users;
    private final CatalogService catalog;
    private final ReviewLikesRepository likes;

    public CommunityService(JdbcTemplate db, UserService users, CatalogService catalog, ReviewLikesRepository likes) {
        this.db = db;
        this.sql = new NamedParameterJdbcTemplate(db);
        this.users = users;
        this.catalog = catalog;
        this.likes = likes;
    }

    public record Review(long entryId, String username, String displayName, Double rating, String review,
                         Instant updatedAt, long likeCount, boolean likedByMe) {}
    public record Summary(Double averageRating, long ratingCount, long reviewCount, List<Review> reviews) {}
    public record Like(long likeCount, boolean likedByMe) {}
    public record FeedItem(long id, String username, String displayName, String avatarUrl,
                           Game game, String action, String status, Double rating, Instant createdAt) {}
    public record FeedPage(List<FeedItem> items, Long nextCursor) {}

    private record ReviewContent(long entryId, String username, String displayName, Double rating,
                                 String review, Instant updatedAt) {
        Review withLikes(ReviewLikesRepository.Likes likes) {
            return new Review(entryId, username, displayName, rating, review, updatedAt, likes.count(), likes.likedByMe());
        }
    }

    @Transactional(readOnly = true)
    public Summary reviews(Long gameId, String viewerName) {
        catalog.find(gameId);
        Long viewerId = viewerName == null ? null : users.require(viewerName).getId();
        var params = Map.of("viewer", viewerId == null ? -1 : viewerId, "game", gameId);
        String visible = " FROM diary_entries d JOIN app_users u ON u.id=d.user_id WHERE d.game_id=:game AND " + ReviewVisibility.SQL;
        var stats = sql.queryForMap("SELECT AVG(d.rating) AS mean,COUNT(d.rating) AS ratings,"
            + "COUNT(CASE WHEN TRIM(COALESCE(d.review,''))<>'' THEN 1 END) AS reviews" + visible, params);
        var rows = sql.query("SELECT d.id,u.username,u.display_name,d.rating,d.review,d.updated_at" + visible
            + " AND (d.rating IS NOT NULL OR TRIM(COALESCE(d.review,''))<>'') ORDER BY d.updated_at DESC,d.id DESC LIMIT " + REVIEW_LIMIT,
            params, (row, index) -> new ReviewContent(row.getLong("id"), row.getString("username"),
                row.getString("display_name"), row.getObject("rating", Double.class),
                row.getString("review"), row.getTimestamp("updated_at").toInstant()));
        var summaries = likes.summaries(rows.stream().map(ReviewContent::entryId).toList(), viewerId);
        var reviews = rows.stream().map(row -> row.withLikes(summaries.getOrDefault(row.entryId(), ReviewLikesRepository.NONE))).toList();
        Double average = stats.get("mean") == null ? null : ((Number) stats.get("mean")).doubleValue();
        return new Summary(average, ((Number) stats.get("ratings")).longValue(),
            ((Number) stats.get("reviews")).longValue(), reviews);
    }

    @Transactional
    public Like like(String viewerName, long entryId, boolean add) {
        long viewer = users.require(viewerName).getId();
        // Serialize concurrent likes and deletion using the same diary-entry lock.
        var entry = db.queryForList("SELECT user_id FROM diary_entries WHERE id=? FOR UPDATE", entryId);
        if (entry.isEmpty()) throw new NotFoundException("Resenha não encontrada.");
        if (((Number) entry.get(0).get("user_id")).longValue() == viewer) {
            throw new BadRequestException("Você não pode curtir sua própria resenha.");
        }
        var params = Map.of("viewer", viewer, "entry", entryId);
        long visible = sql.queryForObject("SELECT COUNT(*) FROM diary_entries d JOIN app_users u ON u.id=d.user_id "
            + "WHERE d.id=:entry AND TRIM(COALESCE(d.review,''))<>'' AND " + ReviewVisibility.SQL, params, Long.class);
        if (visible == 0) throw new NotFoundException("Resenha não encontrada.");
        if (add) {
            long existing = db.queryForObject("SELECT COUNT(*) FROM review_likes WHERE entry_id=? AND user_id=?", Long.class, entryId, viewer);
            if (existing == 0) db.update("INSERT INTO review_likes(entry_id,user_id) VALUES (?,?)", entryId, viewer);
        } else {
            db.update("DELETE FROM review_likes WHERE entry_id=? AND user_id=?", entryId, viewer);
        }
        return new Like(likes.count(entryId), add);
    }

    public void record(DiaryEntry entry, PlayStatus oldStatus, Double oldRating, String oldReview) {
        String action = activityAction(entry, oldStatus, oldRating, oldReview);
        if (action != null) {
            db.update("INSERT INTO activity_events(entry_id,actor_id,game_id,action,status,rating) VALUES (?,?,?,?,?,?)",
                entry.getId(), entry.getUserId(), entry.getGameId(), action, entry.getStatus().name(), entry.getRating());
        }
    }

    private String activityAction(DiaryEntry entry, PlayStatus oldStatus, Double oldRating, String oldReview) {
        if (!Objects.equals(oldReview, entry.getReview()) && !entry.getReview().isBlank()) return "REVIEW";
        if (!Objects.equals(oldRating, entry.getRating()) && entry.getRating() != null) return "RATING";
        if (oldStatus != entry.getStatus() && entry.getStatus() != PlayStatus.WANT_TO_PLAY) return "STATUS";
        return null;
    }

    @Transactional(readOnly = true)
    public FeedPage feed(String viewerName, Long before) {
        long viewer = users.require(viewerName).getId();
        var params = Map.of("viewer", viewer, "before", before == null ? Long.MAX_VALUE : before);
        var games = new HashMap<Long, Game>();
        var items = sql.query("""
            SELECT a.id,u.username,u.display_name,u.avatar_url,a.game_id,a.action,a.status,a.rating,a.created_at
            FROM activity_events a JOIN diary_entries d ON d.id=a.entry_id JOIN app_users u ON u.id=a.actor_id
            WHERE a.id<:before AND
            """ + ReviewVisibility.SQL + """
            AND (u.id=:viewer OR EXISTS(SELECT 1 FROM user_follows f WHERE f.follower_id=:viewer AND f.followed_id=u.id)
                OR EXISTS(SELECT 1 FROM friendships f WHERE f.status='ACCEPTED' AND
                    ((f.first_id=:viewer AND f.second_id=u.id) OR (f.second_id=:viewer AND f.first_id=u.id))))
            ORDER BY a.id DESC LIMIT
            """ + (FEED_PAGE_SIZE + 1), params, (row, index) -> new FeedItem(
                row.getLong("id"), row.getString("username"), row.getString("display_name"), row.getString("avatar_url"),
                games.computeIfAbsent(row.getLong("game_id"), catalog::find), row.getString("action"),
                row.getString("status"), row.getObject("rating", Double.class), row.getTimestamp("created_at").toInstant()));
        var page = items.stream().limit(FEED_PAGE_SIZE).toList();
        return new FeedPage(page, items.size() > FEED_PAGE_SIZE ? page.get(page.size() - 1).id() : null);
    }
}
