package br.com.critplay.social;
import br.com.critplay.users.*;
import br.com.critplay.shared.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class BlockService {
    private final UserService users;private final JdbcTemplate db;
    public BlockService(UserService users,JdbcTemplate db){this.users=users;this.db=db;}
    public boolean blocked(Long a,Long b){return db.queryForObject("SELECT COUNT(*) FROM user_blocks WHERE (blocker_id=? AND blocked_id=?) OR (blocker_id=? AND blocked_id=?)",Long.class,a,b,b,a)>0;}
    public List<Map<String,Object>> list(String actor){long id=users.require(actor).getId();return db.query("SELECT u.username,u.display_name FROM user_blocks b JOIN app_users u ON u.id=b.blocked_id WHERE b.blocker_id=? ORDER BY u.username",(rs,row)->Map.of("username",rs.getString(1),"displayName",rs.getString(2)),id);}
    @Transactional public void change(String actor,String targetName,boolean block){
        long a=users.require(actor).getId(),b=users.require(targetName).getId();if(a==b)throw new BadRequestException("Você não pode bloquear a si mesmo.");
        db.queryForList("SELECT id FROM app_users WHERE id IN (?,?) ORDER BY id FOR UPDATE",Math.min(a,b),Math.max(a,b));
        if(block){if(db.queryForObject("SELECT COUNT(*) FROM user_blocks WHERE blocker_id=? AND blocked_id=?",Long.class,a,b)==0)db.update("INSERT INTO user_blocks(blocker_id,blocked_id) VALUES (?,?)",a,b);
            db.update("DELETE FROM user_follows WHERE (follower_id=? AND followed_id=?) OR (follower_id=? AND followed_id=?)",a,b,b,a);
            db.update("DELETE FROM friendships WHERE first_id=? AND second_id=?",Math.min(a,b),Math.max(a,b));
        }else db.update("DELETE FROM user_blocks WHERE blocker_id=? AND blocked_id=?",a,b);
    }
}
