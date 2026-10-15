package br.com.critplay.social;

import br.com.critplay.users.UserService;
import br.com.critplay.catalog.CatalogService;
import br.com.critplay.diary.DiaryEntry;
import br.com.critplay.diary.PlayStatus;
import br.com.critplay.shared.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.Instant;

@Service
public class CommunityService {
    private final JdbcTemplate db;
    private final NamedParameterJdbcTemplate sql;
    private final UserService users;
    private final CatalogService catalog;
    public CommunityService(JdbcTemplate db, UserService users, CatalogService catalog) {
        this.db=db;this.sql=new NamedParameterJdbcTemplate(db);this.users=users;this.catalog=catalog;
    }
    public record Review(long entryId, String username, String displayName, Double rating, String review,
        Instant updatedAt, long likeCount, boolean likedByMe) {}
    public record Summary(Double averageRating, long ratingCount, long reviewCount, List<Review> reviews) {}
    public record Like(long likeCount, boolean likedByMe) {}
    public record FeedItem(long id, String username, String displayName, String avatarUrl,
        br.com.critplay.catalog.Game game, String action, String status, Double rating, Instant createdAt) {}
    public record FeedPage(List<FeedItem> items, Long nextCursor) {}

    @Transactional(readOnly=true)
    public Summary reviews(Long gameId, String viewerName) {
        catalog.find(gameId);
        long viewer=viewerName==null?-1:users.require(viewerName).getId();
        var params=Map.of("viewer",viewer,"game",gameId);
        String visible=" FROM diary_entries d JOIN app_users u ON u.id=d.user_id WHERE d.game_id=:game AND "+ReviewVisibility.SQL;
        var stats=sql.queryForMap("SELECT AVG(d.rating) AS mean,COUNT(d.rating) AS ratings,"+
            "COUNT(CASE WHEN TRIM(COALESCE(d.review,''))<>'' THEN 1 END) AS reviews"+visible,params);
        var reviews=sql.query("SELECT d.id,u.username,u.display_name,d.rating,d.review,d.updated_at,"+
            "(SELECT COUNT(*) FROM review_likes l JOIN app_users liker ON liker.id=l.user_id WHERE l.entry_id=d.id AND liker.enabled=TRUE) AS likes,"+
            "EXISTS(SELECT 1 FROM review_likes l WHERE l.entry_id=d.id AND l.user_id=:viewer) AS liked"+
            visible+" AND (d.rating IS NOT NULL OR TRIM(COALESCE(d.review,''))<>'') ORDER BY d.updated_at DESC,d.id DESC LIMIT 50",params,
            (rs,row)->new Review(rs.getLong("id"),rs.getString("username"),rs.getString("display_name"),rs.getObject("rating",Double.class),
                rs.getString("review"),rs.getTimestamp("updated_at").toInstant(),rs.getLong("likes"),rs.getBoolean("liked")));
        return new Summary(stats.get("mean")==null?null:((Number)stats.get("mean")).doubleValue(),
            ((Number)stats.get("ratings")).longValue(),((Number)stats.get("reviews")).longValue(),reviews);
    }
    @Transactional
    public Like like(String viewerName, long entryId, boolean add) {
        long viewer=users.require(viewerName).getId();
        // Lock the entry: concurrent likes are idempotent and serialize with deletion.
        var entry=db.queryForList("SELECT user_id FROM diary_entries WHERE id=? FOR UPDATE",entryId);
        if(entry.isEmpty())throw new NotFoundException("Resenha não encontrada.");
        if(((Number)entry.get(0).get("user_id")).longValue()==viewer)throw new BadRequestException("Você não pode curtir sua própria resenha.");
        var params=Map.of("viewer",viewer,"entry",entryId);
        if(sql.queryForObject("SELECT COUNT(*) FROM diary_entries d JOIN app_users u ON u.id=d.user_id WHERE d.id=:entry AND TRIM(COALESCE(d.review,''))<>'' AND "+ReviewVisibility.SQL,params,Long.class)==0)
            throw new NotFoundException("Resenha não encontrada.");
        if(add&&db.queryForObject("SELECT COUNT(*) FROM review_likes WHERE entry_id=? AND user_id=?",Long.class,entryId,viewer)==0)
            db.update("INSERT INTO review_likes(entry_id,user_id) VALUES (?,?)",entryId,viewer);
        if(!add)db.update("DELETE FROM review_likes WHERE entry_id=? AND user_id=?",entryId,viewer);
        long count=db.queryForObject("SELECT COUNT(*) FROM review_likes l JOIN app_users u ON u.id=l.user_id WHERE l.entry_id=? AND u.enabled=TRUE",Long.class,entryId);
        return new Like(count,add);
    }
    public void record(DiaryEntry entry, PlayStatus oldStatus, Double oldRating, String oldReview) {
        String action=null;
        if(!Objects.equals(oldReview,entry.getReview())&&!entry.getReview().isBlank())action="REVIEW";
        else if(!Objects.equals(oldRating,entry.getRating())&&entry.getRating()!=null)action="RATING";
        else if(oldStatus!=entry.getStatus()&&entry.getStatus()!=PlayStatus.WANT_TO_PLAY)action="STATUS";
        if(action!=null)db.update("INSERT INTO activity_events(entry_id,actor_id,game_id,action,status,rating) VALUES (?,?,?,?,?,?)",
            entry.getId(),entry.getUserId(),entry.getGameId(),action,entry.getStatus().name(),entry.getRating());
    }
    @Transactional(readOnly=true)
    public FeedPage feed(String viewerName, Long before) {
        long viewer=users.require(viewerName).getId();
        var params=Map.of("viewer",viewer,"before",before==null?Long.MAX_VALUE:before);
        var items=sql.query("""
            SELECT a.id,u.username,u.display_name,u.avatar_url,a.game_id,a.action,a.status,a.rating,a.created_at
            FROM activity_events a JOIN diary_entries d ON d.id=a.entry_id JOIN app_users u ON u.id=a.actor_id
            WHERE a.id<:before AND
            """+ReviewVisibility.SQL+"""
            AND (u.id=:viewer OR EXISTS(SELECT 1 FROM user_follows f WHERE f.follower_id=:viewer AND f.followed_id=u.id)
                OR EXISTS(SELECT 1 FROM friendships f WHERE f.status='ACCEPTED' AND
                    ((f.first_id=:viewer AND f.second_id=u.id) OR (f.second_id=:viewer AND f.first_id=u.id))))
            ORDER BY a.id DESC LIMIT 26
            """,params,(rs,row)->new FeedItem(rs.getLong("id"),rs.getString("username"),rs.getString("display_name"),rs.getString("avatar_url"),
                catalog.find(rs.getLong("game_id")),rs.getString("action"),rs.getString("status"),rs.getObject("rating",Double.class),rs.getTimestamp("created_at").toInstant()));
        boolean more=items.size()>25;var page=items.stream().limit(25).toList();
        return new FeedPage(page,more?page.get(page.size()-1).id():null);
    }
}
