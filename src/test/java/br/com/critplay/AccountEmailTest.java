package br.com.critplay;

import br.com.critplay.users.*;
import br.com.critplay.profile.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.config.import=","spring.datasource.url=jdbc:h2:mem:account-mail;DB_CLOSE_DELAY=-1",
    "app.mail.notifications-enabled=true","app.admin.username=","app.admin.password=","app.demo.enabled=false"})
class AccountEmailTest {
    @Autowired UserService users;
    @Autowired ProfileService profiles;
    @Autowired PasswordResetService resets;
    @Autowired AccountNotifications notices;
    @Autowired AccountNotificationDelivery delivery;
    @Autowired JdbcTemplate db;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean TransactionalMailer mail;
    @MockitoBean ResetMailer resetMail;
    @BeforeEach void configured(){when(mail.configured()).thenReturn(true);when(resetMail.enabled()).thenReturn(true);}
    UserView account(){String name="email_"+UUID.randomUUID().toString().substring(0,8);return users.register(new RegisterRequest(name,"Pessoa de teste","EmailPassword42!",name+"@example.test"));}
    @Test void welcomeIsCommittedAndPasswordChangeNotifiesWithoutPassword(){
        var user=account();
        assertEquals("SENT",db.queryForObject("SELECT status FROM account_email_outbox WHERE username=? AND kind='WELCOME'",String.class,user.username()));
        profiles.changePassword(user.username(),new PasswordChangeRequest("EmailPassword42!","DifferentPassword42!"));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM account_email_outbox WHERE username=? AND kind='PASSWORD_CHANGED' AND status='SENT'",Integer.class,user.username()));
        verify(mail).send(eq(user.username()+"@example.test"),eq("Sua senha do CritPlay foi alterada"),argThat(text->!text.contains("DifferentPassword42!")&&!text.contains("EmailPassword42!")),startsWith("notice-"));
    }
    @Test void failedNoticePreservesAccountAndCanBeRetried(){
        doThrow(new br.com.critplay.shared.ServiceUnavailableException("provider down")).when(mail).send(anyString(),anyString(),anyString(),anyString());
        var user=account();assertNotNull(users.require(user.username()));
        var id=db.queryForObject("SELECT id FROM account_email_outbox WHERE username=?",String.class,user.username());
        assertEquals("PENDING",db.queryForObject("SELECT status FROM account_email_outbox WHERE id=?",String.class,id));
        doNothing().when(mail).send(anyString(),anyString(),anyString(),anyString());
        db.update("UPDATE account_email_outbox SET last_attempt_at=NULL WHERE id=?",id);delivery.attempt(id);
        assertEquals("SENT",db.queryForObject("SELECT status FROM account_email_outbox WHERE id=?",String.class,id));
        assertEquals(2,db.queryForObject("SELECT attempts FROM account_email_outbox WHERE id=?",Integer.class,id));
    }
    @Test void rollbackDoesNotSendOrPersistNotice(){
        var id="rollback_"+UUID.randomUUID().toString().substring(0,8);
        new TransactionTemplate(transactions).executeWithoutResult(status->{notices.queue("rollback@example.test",id,AccountNotifications.Kind.WELCOME);status.setRollbackOnly();});
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM account_email_outbox WHERE username=?",Integer.class,id));
        verify(mail,never()).send(anyString(),anyString(),anyString(),anyString());
    }
    @Test void emailChangeNotifiesOldAndNewAddressesAndRequiresPassword(){
        var user=account();String email="new_"+user.username()+"@example.test";
        assertThrows(br.com.critplay.shared.BadRequestException.class,()->profiles.update(user.username(),new ProfileUpdateRequest("Pessoa","",email,null,"wrong")));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM account_email_outbox WHERE username=?",Integer.class,user.username()));
        profiles.update(user.username(),new ProfileUpdateRequest("Pessoa","",email,null,"EmailPassword42!"));
        assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM account_email_outbox WHERE username=? AND status='SENT'",Integer.class,user.username()));
        assertEquals(email,users.require(user.username()).getEmail());
    }
    @Test void recoveryLinkIsSingleUseAndOutboxNeverStoresToken(){
        var user=account();var url=org.mockito.ArgumentCaptor.forClass(String.class);
        resets.request(user.username(),user.username()+"@example.test");verify(resetMail).send(anyString(),url.capture());
        String token=url.getValue().substring(url.getValue().indexOf("#reset=")+7);
        assertEquals(43,token.length());assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM password_reset_tokens WHERE token_hash=?",Integer.class,token));
        resets.reset(token,"RecoveredPassword42!");assertThrows(br.com.critplay.shared.BadRequestException.class,()->resets.reset(token,"RecoveredPassword42!"));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM account_email_outbox WHERE username=? AND kind='PASSWORD_CHANGED' AND status='SENT'",Integer.class,user.username()));
    }
}
