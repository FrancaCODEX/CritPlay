package br.com.critplay.users;
import br.com.critplay.shared.ServiceUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class ResetMailer {
    private final TransactionalMailer transport;private final boolean configured;
    public ResetMailer(TransactionalMailer transport,@Value("${app.recovery.enabled}") boolean enabled) {
        this.transport=transport;this.configured=enabled&&transport.configured();
    }
    public boolean enabled() { return configured; }
    public void send(String email,String url) {
        if(!configured) throw new ServiceUnavailableException("Recuperação por e-mail ainda não foi configurada.");
        try {
            transport.send(email,"CritPlay — redefinir sua senha",
                "Você solicitou uma nova senha do CritPlay. O link vale por 15 minutos e pode ser usado uma única vez:\n"+url
                +"\nSe você não fez este pedido, ignore esta mensagem.","reset-"+java.util.UUID.randomUUID());
        } catch(Exception ex) { throw new ServiceUnavailableException("Não foi possível enviar o e-mail agora."); }
    }
}
