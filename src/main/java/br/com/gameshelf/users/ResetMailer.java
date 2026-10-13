package br.com.gameshelf.users;
import br.com.gameshelf.shared.ServiceUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class ResetMailer {
    private final ObjectProvider<JavaMailSender> sender;private final boolean configured;private final String from;
    public ResetMailer(ObjectProvider<JavaMailSender> sender,@Value("${app.recovery.enabled}") boolean enabled,
        @Value("${spring.mail.host}") String host,@Value("${app.recovery.from}") String from) {
        this.sender=sender;this.configured=enabled&&!host.isBlank()&&!from.isBlank();this.from=from;
    }
    public boolean enabled() { return configured; }
    public void send(String email,String url) {
        if(!configured) throw new ServiceUnavailableException("Recuperação por e-mail ainda não foi configurada.");
        try {
            var message=new SimpleMailMessage();message.setFrom(from);message.setTo(email);
            message.setSubject("GameShelf — redefinir sua senha");
            message.setText("Você solicitou uma nova senha do GameShelf. O link vale por 15 minutos e pode ser usado uma única vez:\n"+url
                +"\nSe você não fez este pedido, ignore esta mensagem.");
            sender.getObject().send(message);
        } catch(Exception ex) { throw new ServiceUnavailableException("Não foi possível enviar o e-mail agora."); }
    }
}
