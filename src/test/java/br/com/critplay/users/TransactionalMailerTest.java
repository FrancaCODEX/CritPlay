package br.com.critplay.users;

import br.com.critplay.shared.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import org.springframework.http.*;

class TransactionalMailerTest {
    @SuppressWarnings("unchecked")
    private ObjectProvider<JavaMailSender> smtp(){return mock(ObjectProvider.class);}
    @Test void sendsSmtpWithoutCallingHttp(){
        var provider=smtp();var sender=mock(JavaMailSender.class);when(provider.getObject()).thenReturn(sender);
        var mail=new TransactionalMailer(provider,"smtp","smtp.example.test","from@example.test","",RestClient.builder());
        mail.send("to@example.test","Aviso","Texto seguro","notice-id");
        var message=org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);verify(sender).send(message.capture());
        assertEquals("from@example.test",message.getValue().getFrom());assertArrayEquals(new String[]{"to@example.test"},message.getValue().getTo());assertEquals("Texto seguro",message.getValue().getText());
    }
    @Test void sendsResendWithServerSideKeyAndIdempotency(){
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.resend.com/emails")).andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization","Bearer private-test-key")).andExpect(header("Idempotency-Key","notice-id"))
            .andExpect(content().json("{\"from\":\"from@example.test\",\"to\":[\"to@example.test\"],\"subject\":\"Aviso\",\"text\":\"Texto seguro\"}"))
            .andRespond(withSuccess("{\"id\":\"accepted\"}",MediaType.APPLICATION_JSON));
        var mail=new TransactionalMailer(smtp(),"resend","","from@example.test","private-test-key",builder);
        assertTrue(mail.configured());mail.send("to@example.test","Aviso","Texto seguro","notice-id");server.verify();
    }
    @Test void sanitizesProviderErrorAndRejectsMissingConfiguration(){
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.resend.com/emails")).andRespond(withBadRequest().body("private-test-key to@example.test"));
        var mail=new TransactionalMailer(smtp(),"resend","","from@example.test","private-test-key",builder);
        var error=assertThrows(ServiceUnavailableException.class,()->mail.send("to@example.test","Aviso","Texto","notice-id"));
        assertFalse(error.getMessage().contains("private-test-key"));assertFalse(error.getMessage().contains("to@example.test"));assertNull(error.getCause());server.verify();
        var disabled=new TransactionalMailer(smtp(),"smtp","","","",RestClient.builder());assertFalse(disabled.configured());
        assertThrows(ServiceUnavailableException.class,()->disabled.send("to@example.test","Aviso","Texto","notice-id"));
    }
}
