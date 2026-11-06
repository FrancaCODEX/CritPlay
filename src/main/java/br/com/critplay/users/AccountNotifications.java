package br.com.critplay.users;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Transactional outbox for notices only. Reset tokens/passwords never enter this table. */
@Service
public class AccountNotifications {
    public record Queued(String id){}
    public enum Kind { WELCOME, EMAIL_ADDED, EMAIL_REMOVED, PASSWORD_CHANGED }
    private final JdbcTemplate db;private final ApplicationEventPublisher events;private final boolean enabled;
    public AccountNotifications(JdbcTemplate db,ApplicationEventPublisher events,
            @Value("${app.mail.notifications-enabled:false}") boolean enabled){this.db=db;this.events=events;this.enabled=enabled;}
    public void queue(String email,String username,Kind kind){
        if(!enabled||email==null||email.isBlank())return;
        String id=UUID.randomUUID().toString();
        db.update("INSERT INTO account_email_outbox(id,recipient,username,kind,created_at) VALUES (?,?,?,?,?)",id,email,username,kind.name(),OffsetDateTime.now());
        events.publishEvent(new Queued(id));
    }
}
