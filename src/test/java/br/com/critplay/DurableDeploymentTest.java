package br.com.critplay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.SessionRepository;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEST_POSTGRES",matches="true")
class DurableDeploymentTest {
    private ConfigurableApplicationContext start(){return new SpringApplicationBuilder(CritPlayApplication.class)
        .profiles("postgres").run("--spring.config.import=","--server.port=0","--app.media.storage=database",
            "--app.admin.username=","--app.admin.password=","--app.demo.enabled=false","--app.catalog.seed-enabled=false",
            "--rawg.api-key=","--ggdeals.api-key=","--app.security.rate-limit-enabled=false");}
    private String base(ConfigurableApplicationContext c){return "http://127.0.0.1:"+((ServletWebServerApplicationContext)c).getWebServer().getPort();}
    @Test @SuppressWarnings({"rawtypes","unchecked"}) void sessionMediaAndRelationsSurviveAnApplicationRestart() throws Exception {
        String marker=UUID.randomUUID().toString();String filename="987-"+marker+".png",sessionId;
        byte[] pixels=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1sAAAAASUVORK5CYII=");
        long owner;
        try(var first=start()) {
            var user=first.getBean(br.com.critplay.users.UserService.class).register(new br.com.critplay.users.RegisterRequest("persist_"+marker.substring(0,8),"Persistência","DurablePassword42!"));owner=user.id();
            var repository=first.getBean(SessionRepository.class);var session=repository.createSession();
            session.setAttribute("restart-marker",marker);
            var principal=new br.com.critplay.security.AccountPrincipal(first.getBean(br.com.critplay.users.UserRepository.class).findById(owner).orElseThrow());
            var security=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
            security.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
            session.setAttribute("SPRING_SECURITY_CONTEXT",security);repository.save(session);sessionId=session.getId();
            Path scratch=Files.createTempDirectory("critplay-durable-");Path image=scratch.resolve(filename);Files.write(image,pixels);
            try{first.getBean(br.com.critplay.media.DatabaseMediaStore.class).save(owner,image,null);}finally{Files.delete(image);Files.delete(scratch);}
            var db=first.getBean(JdbcTemplate.class);
            assertTrue(db.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=TRUE",Long.class)>=12);
            var jobs=first.getBean(br.com.critplay.metadata.ImportJobStore.class);
            var job=jobs.create(owner,500);jobs.recover();assertEquals("RUNNING",jobs.get(job.id()).status());
            db.update("UPDATE import_jobs SET lease_until=? WHERE id=?",java.time.OffsetDateTime.now().minusMinutes(1),job.id());
            jobs.recover();assertEquals("PAUSED",jobs.get(job.id()).status());
            var resumed=jobs.resume(job.id(),owner);assertFalse(jobs.heartbeat(job.id(),job.leaseToken()));
            jobs.pauseWorker(job.id(),job.leaseToken(),"stale");assertEquals("RUNNING",jobs.get(job.id()).status());
            jobs.pauseWorker(resumed.id(),resumed.leaseToken(),"test complete");
        }
        try(var second=start()) {
            var repository=second.getBean(SessionRepository.class);var session=repository.findById(sessionId);
            assertNotNull(session);assertEquals(marker,session.getAttribute("restart-marker"));
            var security=(org.springframework.security.core.context.SecurityContext)session.getAttribute("SPRING_SECURITY_CONTEXT");
            assertEquals(owner,((br.com.critplay.security.AccountPrincipal)security.getAuthentication().getPrincipal()).getAccountId());
            assertArrayEquals(pixels,second.getBean(br.com.critplay.media.DatabaseMediaStore.class).read(filename).orElseThrow());
            assertTrue(second.getBean(br.com.critplay.media.DatabaseMediaStore.class).owned(owner,filename));
            var http=HttpClient.newHttpClient();
            var image=http.send(HttpRequest.newBuilder(URI.create(base(second)+"/media/"+filename)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200,image.statusCode());assertArrayEquals(pixels,image.body());
            assertEquals(200,http.send(HttpRequest.newBuilder(URI.create(base(second)+"/")).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode());
            var cookie=Base64.getEncoder().encodeToString(sessionId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var me=http.send(HttpRequest.newBuilder(URI.create(base(second)+"/api/auth/me")).header("Cookie","JSESSIONID="+cookie).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(200,me.statusCode());assertTrue(me.body().contains("persist_"));
        }
    }
}
