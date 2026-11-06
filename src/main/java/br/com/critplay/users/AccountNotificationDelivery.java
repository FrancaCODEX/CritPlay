package br.com.critplay.users;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class AccountNotificationDelivery {
    private final JdbcTemplate db;private final TransactionalMailer mail;
    public AccountNotificationDelivery(JdbcTemplate db,TransactionalMailer mail){this.db=db;this.mail=mail;}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void attempt(String id){
        var rows=db.queryForList("SELECT * FROM account_email_outbox WHERE id=? AND status='PENDING' AND attempts<5 FOR UPDATE SKIP LOCKED",id);
        if(rows.isEmpty()||!mail.configured())return;
        var row=rows.get(0);var last=db.queryForObject("SELECT CASE WHEN last_attempt_at IS NULL OR last_attempt_at<? THEN 1 ELSE 0 END FROM account_email_outbox WHERE id=?",Integer.class,OffsetDateTime.now().minusMinutes(2),id);
        if(last==0)return;
        if(db.queryForObject("SELECT COUNT(*) FROM account_email_outbox WHERE id=? AND created_at<?",Integer.class,id,OffsetDateTime.now().minusDays(7))>0){db.update("UPDATE account_email_outbox SET status='EXPIRED' WHERE id=?",id);return;}
        var kind=AccountNotifications.Kind.valueOf((String)row.get("kind"));
        String subject=switch(kind){case WELCOME->"Bem-vindo ao CritPlay";case EMAIL_ADDED->"E-mail cadastrado no CritPlay";case EMAIL_REMOVED->"E-mail removido da conta CritPlay";case PASSWORD_CHANGED->"Sua senha do CritPlay foi alterada";};
        String action=switch(kind){case WELCOME->"Sua conta foi criada e este endereço foi cadastrado para avisos e recuperação de senha.";case EMAIL_ADDED->"Este endereço foi cadastrado na sua conta. Este aviso não verifica a propriedade do e-mail.";case EMAIL_REMOVED->"Este endereço deixou de estar associado à sua conta.";case PASSWORD_CHANGED->"A senha da sua conta foi alterada. As sessões anteriores foram encerradas.";};
        String text="Olá, @"+row.get("username")+".\n\n"+action+"\n\nSe você não reconhece esta ação, acesse o CritPlay pelo endereço que já conhece e procure o responsável pelo serviço. Nunca compartilhe sua senha ou links de recuperação.\n\nEquipe CritPlay";
        db.update("UPDATE account_email_outbox SET attempts=attempts+1,last_attempt_at=? WHERE id=?",OffsetDateTime.now(),id);
        try{mail.send((String)row.get("recipient"),subject,text,"notice-"+id);db.update("UPDATE account_email_outbox SET status='SENT',sent_at=? WHERE id=?",OffsetDateTime.now(),id);}
        catch(br.com.critplay.shared.ServiceUnavailableException failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Aviso de conta pendente de envio; confira o provedor de e-mail.");}
    }
}
