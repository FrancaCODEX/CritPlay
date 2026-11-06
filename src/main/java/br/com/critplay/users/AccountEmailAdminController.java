package br.com.critplay.users;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/email")
public class AccountEmailAdminController {
    private final JdbcTemplate db;private final AccountNotificationDelivery delivery;private final TransactionalMailer mail;
    public AccountEmailAdminController(JdbcTemplate db,AccountNotificationDelivery delivery,TransactionalMailer mail){this.db=db;this.delivery=delivery;this.mail=mail;}
    @GetMapping("/status") public Map<String,Object> status(){return Map.of("configured",mail.configured(),"pending",db.queryForObject("SELECT COUNT(*) FROM account_email_outbox WHERE status='PENDING'",Long.class),"exhausted",db.queryForObject("SELECT COUNT(*) FROM account_email_outbox WHERE status='PENDING' AND attempts>=5",Long.class));}
    @PostMapping("/retry") public Map<String,Object> retry(){
        for(String id:db.query("SELECT id FROM account_email_outbox WHERE status='PENDING' AND attempts<5 AND (last_attempt_at IS NULL OR last_attempt_at<?) ORDER BY created_at LIMIT 10",(rs,n)->rs.getString(1),java.time.OffsetDateTime.now().minusMinutes(2)))delivery.attempt(id);
        return status();
    }
}
