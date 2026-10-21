package br.com.critplay.social;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Persistence for relationships. Visibility decisions belong to SocialAccessService. */
@Repository
public class SocialGraphRepository {
    private static final String IDENTITY_COLUMNS = "u.id,u.username,u.display_name,u.avatar_url,u.visibility";
    private static final int PAGE_SIZE = 30;
    private static final String VISIBLE_MEMBER = """
        u.enabled=TRUE AND NOT EXISTS (SELECT 1 FROM user_blocks b
          WHERE (b.blocker_id=? AND b.blocked_id=u.id) OR (b.blocked_id=? AND b.blocker_id=u.id))
        AND (?=? OR u.discoverable=TRUE OR u.id=? OR EXISTS (SELECT 1 FROM friendships friend
          WHERE friend.status='ACCEPTED' AND ((friend.first_id=? AND friend.second_id=u.id) OR (friend.second_id=? AND friend.first_id=u.id))))
        """;
    private final JdbcTemplate db;

    public SocialGraphRepository(JdbcTemplate db) {
        this.db = db;
    }

    public enum FriendshipState { NONE, FRIENDS, OUTGOING, INCOMING }

    public record Relationship(boolean following, FriendshipState friendship) {
        public Map<String, Object> toMap() {
            return Map.of("following", following, "friendship", friendship.name());
        }
    }

    public record Friendship(PersonIdentity person, long requesterId, boolean accepted) {}

    public List<PersonIdentity> search(long viewer, String term) {
        // Apply the same 30-candidate limit before excluding blocked users, as the existing API did.
        return db.query("SELECT " + IDENTITY_COLUMNS + """
             FROM (SELECT id,username,display_name,avatar_url,visibility FROM app_users WHERE enabled=TRUE AND discoverable=TRUE
               AND (LOWER(username) LIKE ? ESCAPE '!' OR LOWER(display_name) LIKE ? ESCAPE '!')
               ORDER BY username LIMIT 30) u
             WHERE NOT EXISTS (SELECT 1 FROM user_blocks b WHERE
               (b.blocker_id=? AND b.blocked_id=u.id) OR (b.blocked_id=? AND b.blocker_id=u.id))
             ORDER BY u.username
            """, (row, index) -> PersonIdentity.read(row), term, term, viewer, viewer);
    }

    public boolean areFriends(long first, long second) {
        return count("SELECT COUNT(*) FROM friendships WHERE first_id=? AND second_id=? AND status='ACCEPTED'",
            Math.min(first, second), Math.max(first, second)) > 0;
    }

    public Relationship relationship(long actor, long target) {
        var rows = db.queryForList("SELECT requester_id,status FROM friendships WHERE first_id=? AND second_id=?",
            Math.min(actor, target), Math.max(actor, target));
        FriendshipState friendship = FriendshipState.NONE;
        if (!rows.isEmpty()) {
            var row = rows.get(0);
            friendship = "ACCEPTED".equals(row.get("status")) ? FriendshipState.FRIENDS
                : ((Number) row.get("requester_id")).longValue() == actor
                    ? FriendshipState.OUTGOING : FriendshipState.INCOMING;
        }
        return new Relationship(isFollowing(actor, target), friendship);
    }

    public List<PersonIdentity> connections(long owner, boolean followers) {
        String person = followers ? "follower_id" : "followed_id";
        String target = followers ? "followed_id" : "follower_id";
        return db.query("SELECT " + IDENTITY_COLUMNS + " FROM user_follows f JOIN app_users u ON u.id=f." + person
            + " WHERE f." + target + "=? ORDER BY u.id", (row, index) -> PersonIdentity.read(row), owner);
    }

    public List<Friendship> friendships(long owner) {
        return db.query("SELECT " + IDENTITY_COLUMNS + """
            ,f.requester_id,f.status FROM friendships f JOIN app_users u ON u.id=
              CASE WHEN f.first_id=? THEN f.second_id ELSE f.first_id END
            WHERE f.first_id=? OR f.second_id=? ORDER BY f.first_id,f.second_id
            """, (row, index) -> new Friendship(PersonIdentity.read(row), row.getLong("requester_id"),
                "ACCEPTED".equals(row.getString("status"))), owner, owner, owner);
    }

    public Map<String, Object> counts(long viewer, long owner) {
        return Map.of("followers", followCount(viewer, owner, true),
            "following", followCount(viewer, owner, false),
            "friends", count("SELECT COUNT(*) FROM friendships WHERE status='ACCEPTED' AND (first_id=? OR second_id=?)", owner, owner));
    }

    public Map<String, Object> followPage(long viewer, long owner, boolean followers, long after) {
        var args = new ArrayList<Object>(Arrays.asList(followArgs(viewer, owner)));
        args.add(after);
        var rows = db.query("SELECT " + IDENTITY_COLUMNS + followQuery(followers)
            + " AND u.id>? ORDER BY u.id LIMIT " + (PAGE_SIZE + 1),
            (row, index) -> PersonIdentity.read(row), args.toArray());
        var items = rows.stream().limit(PAGE_SIZE).map(PersonIdentity::toMap).toList();
        var page = new java.util.LinkedHashMap<String, Object>();
        page.put("items", items);
        page.put("total", followCount(viewer, owner, followers));
        page.put("nextCursor", rows.size() > PAGE_SIZE ? rows.get(PAGE_SIZE - 1).id() : null);
        return page;
    }

    public void lockPair(long first, long second) {
        db.queryForList("SELECT id FROM app_users WHERE id IN (?,?) ORDER BY id FOR UPDATE",
            Math.min(first, second), Math.max(first, second));
    }

    public boolean hasConnection(long first, long second) {
        return count("SELECT COUNT(*) FROM user_follows WHERE (follower_id=? AND followed_id=?) OR (follower_id=? AND followed_id=?)",
            first, second, second, first) > 0
            || count("SELECT COUNT(*) FROM friendships WHERE first_id=? AND second_id=?",
                Math.min(first, second), Math.max(first, second)) > 0;
    }

    public boolean isEnabled(long user) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT enabled FROM app_users WHERE id=?", Boolean.class, user));
    }

    public void follow(long actor, long target) {
        if (!isFollowing(actor, target)) {
            db.update("INSERT INTO user_follows(follower_id,followed_id) VALUES (?,?)", actor, target);
        }
    }

    public void unfollow(long actor, long target) {
        db.update("DELETE FROM user_follows WHERE follower_id=? AND followed_id=?", actor, target);
    }

    public void requestFriendship(long actor, long target) {
        db.update("INSERT INTO friendships(first_id,second_id,requester_id,status) VALUES (?,?,?,'PENDING')",
            Math.min(actor, target), Math.max(actor, target), actor);
    }

    public void acceptFriendship(long first, long second) {
        db.update("UPDATE friendships SET status='ACCEPTED' WHERE first_id=? AND second_id=?",
            Math.min(first, second), Math.max(first, second));
    }

    public void removeFriendship(long first, long second) {
        db.update("DELETE FROM friendships WHERE first_id=? AND second_id=?",
            Math.min(first, second), Math.max(first, second));
    }

    private boolean isFollowing(long actor, long target) {
        return count("SELECT COUNT(*) FROM user_follows WHERE follower_id=? AND followed_id=?", actor, target) > 0;
    }

    private long followCount(long viewer, long owner, boolean followers) {
        return count("SELECT COUNT(*)" + followQuery(followers), followArgs(viewer, owner));
    }

    private String followQuery(boolean followers) {
        return " FROM user_follows f JOIN app_users u ON u.id=f." + (followers ? "follower_id" : "followed_id")
            + " WHERE f." + (followers ? "followed_id" : "follower_id") + "=? AND " + VISIBLE_MEMBER;
    }

    private Object[] followArgs(long viewer, long owner) {
        return new Object[]{owner, viewer, viewer, viewer, owner, viewer, viewer, viewer};
    }

    private long count(String query, Object... args) {
        return db.queryForObject(query, Long.class, args);
    }
}
