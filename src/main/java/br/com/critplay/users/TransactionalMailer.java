package br.com.critplay.users;

import br.com.critplay.shared.ServiceUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import java.util.*;

/** Only server-owned URLs and headers; provider errors never expose keys or recipients. */
@Service
public class TransactionalMailer {
    private final ObjectProvider<JavaMailSender> smtp;
    private final String provider,host,from,key;
    private final RestClient http;
    @org.springframework.beans.factory.annotation.Autowired
    public TransactionalMailer(ObjectProvider<JavaMailSender> smtp,
            @Value("${app.mail.provider:smtp}") String provider,@Value("${spring.mail.host:}") String host,
            @Value("${app.recovery.from:}") String from,@Value("${app.mail.api-key:}") String key) {
        this(smtp,provider,host,from,key,httpBuilder());
    }
    TransactionalMailer(ObjectProvider<JavaMailSender> smtp,String provider,String host,String from,String key,RestClient.Builder builder) {
        this.smtp=smtp;this.provider=provider;this.host=host;this.from=from;this.key=key;http=builder.build();
    }
    private static RestClient.Builder httpBuilder(){
        var factory=new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));return RestClient.builder().requestFactory(factory);
    }
    public boolean configured(){return !from.isBlank()&&(provider.equals("smtp")?!host.isBlank():provider.equals("resend")&&!key.isBlank());}
    public void send(String recipient,String subject,String text,String deliveryId) {
        if(!configured())throw new ServiceUnavailableException("Envio de e-mail ainda não foi configurado.");
        try {
            if(provider.equals("smtp")) {
                var msg=new SimpleMailMessage();msg.setFrom(from);msg.setTo(recipient);msg.setSubject(subject);msg.setText(text);smtp.getObject().send(msg);
            } else {
                http.post().uri("https://api.resend.com/emails").header("Authorization","Bearer "+key)
                    .header("Idempotency-Key",deliveryId).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of("from",from,"to",List.of(recipient),"subject",subject,"text",text)).retrieve().toBodilessEntity();
            }
        } catch(Exception failure){throw new ServiceUnavailableException("Não foi possível enviar o e-mail agora.");}
    }
}
