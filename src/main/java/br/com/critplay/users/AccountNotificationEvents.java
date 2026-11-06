package br.com.critplay.users;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AccountNotificationEvents {
    private final AccountNotificationDelivery delivery;
    public AccountNotificationEvents(AccountNotificationDelivery delivery){this.delivery=delivery;}
    @TransactionalEventListener
    public void committed(AccountNotifications.Queued event){
        try{delivery.attempt(event.id());}
        catch(Exception failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Aviso de conta preservado para nova tentativa.");}
    }
}
