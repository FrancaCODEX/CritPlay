package br.com.critplay.social;

/** SQL shared by reviews, likes and activities; aliases d=diary, u=author. */
public final class ReviewVisibility {
    private ReviewVisibility() {}
    public static final String SQL = """
        u.enabled=TRUE AND d.review_hidden=FALSE
        AND NOT EXISTS (SELECT 1 FROM user_blocks b WHERE
            (b.blocker_id=:viewer AND b.blocked_id=u.id) OR (b.blocked_id=:viewer AND b.blocker_id=u.id))
        AND (u.id=:viewer OR ((u.discoverable=TRUE OR EXISTS (SELECT 1 FROM friendships f
            WHERE f.status='ACCEPTED' AND ((f.first_id=:viewer AND f.second_id=u.id) OR (f.second_id=:viewer AND f.first_id=u.id))))
            AND (u.visibility='PUBLIC' OR (u.visibility='FRIENDS' AND EXISTS (SELECT 1 FROM friendships f
            WHERE f.status='ACCEPTED' AND ((f.first_id=:viewer AND f.second_id=u.id) OR (f.second_id=:viewer AND f.first_id=u.id)))))))
        """;
}
