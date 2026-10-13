package br.com.gameshelf;

import br.com.gameshelf.users.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:tests;DB_CLOSE_DELAY=-1", "ggdeals.api-key=",
    "app.security.rate-limit-enabled=false", "app.admin.username=test_admin", "app.admin.password=AdminTestPassword42!"
})
class ApiIntegrationTest {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired PasswordResetRepository resets;
    @MockitoBean ResetMailer mail;
    @BeforeEach void mailOff() { when(mail.enabled()).thenReturn(false); }
    final ObjectMapper mapper = new ObjectMapper();
    final String gameJson = """
        {"title":"Jogo de teste","genre":"Aventura","platforms":["PC","XBOX"],
         "releaseYear":2026,"steamAppId":420,"description":"Descrição de teste para o novo jogo do catálogo."}
        """;
    class Session {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();
        String csrf, csrfHeader;
        HttpResponse<String> send(String method, String path, String body) throws Exception {
            return send(method,path,body,true,"application/json");
        }
        HttpResponse<String> send(String method, String path, String body, boolean withCsrf, String type) throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
                .header("Content-Type",type);
            if (withCsrf && csrf != null && !method.equals("GET")) request.header(csrfHeader,csrf);
            return http.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        }
        void refreshCsrf() throws Exception {
            var response = send("GET","/api/auth/csrf",null);
            assertEquals(200,response.statusCode());
            var json = mapper.readTree(response.body());
            csrf=json.path("token").asText();csrfHeader=json.path("headerName").asText();
        }
        void login(String username, String password) throws Exception {
            refreshCsrf();
            String form="username="+URLEncoder.encode(username,java.nio.charset.StandardCharsets.UTF_8)
                +"&password="+URLEncoder.encode(password,java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(200,send("POST","/api/auth/login",form,true,"application/x-www-form-urlencoded").statusCode());
            refreshCsrf();
        }
        JsonNode json(String path) throws Exception {
            var response=send("GET",path,null);assertEquals(200,response.statusCode());
            return mapper.readTree(response.body());
        }
        HttpResponse<String> upload(byte[] data,String filename,String mime) throws Exception {
            String boundary="gameshelf-test-boundary";
            var bytes=new java.io.ByteArrayOutputStream();
            bytes.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+filename+"\"\r\nContent-Type: "+mime+"\r\n\r\n").getBytes());
            bytes.write(data);bytes.write(("\r\n--"+boundary+"--\r\n").getBytes());
            return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/images"))
                .header("Content-Type","multipart/form-data; boundary="+boundary).header(csrfHeader,csrf)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
        }
        String sessionId() {
            return cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("JSESSIONID"))
                .map(HttpCookie::getValue).findFirst().orElse("");
        }
    }
    Session user() throws Exception {
        var session=new Session();session.refreshCsrf();
        String username="user_"+UUID.randomUUID().toString().replace("-","").substring(0,10);
        var body=mapper.writeValueAsString(Map.of("username",username,"displayName","Pessoa de teste","password","UserTestPassword42!"));
        assertEquals(201,session.send("POST","/api/auth/register",body).statusCode());
        session.login(username,"UserTestPassword42!");return session;
    }
    Session admin() throws Exception {
        var session=new Session();session.login("test_admin","AdminTestPassword42!");return session;
    }
    @Test void homeAndCatalogArePublic() throws Exception {
        var session=new Session();
        assertEquals(200,session.send("GET","/",null).statusCode());
        assertTrue(session.json("/api/games").size()>=12);
        assertFalse(session.json("/api/auth/me").path("authenticated").asBoolean());
    }
    @Test void combinesTitleGenreAndPlatformFilters() throws Exception {
        var session=new Session();
        assertEquals(1,session.json("/api/games?q=elden&genre=RPG&platform=PC").size());
        assertEquals(0,session.json("/api/games?q=elden&platform=Nintendo").size());
    }
    @Test void anonymousCannotReadPrivateDataOrCreateGames() throws Exception {
        var session=new Session();session.refreshCsrf();
        assertEquals(401,session.send("GET","/api/diary",null).statusCode());
        assertEquals(401,session.send("GET","/api/games/1/prices",null).statusCode());
        assertEquals(401,session.send("POST","/api/games",gameJson).statusCode());
    }
    @Test void registrationCannotGrantAdminAndNeverReturnsPassword() throws Exception {
        var session=new Session();session.refreshCsrf();
        var attempted=mapper.writeValueAsString(Map.of("username","forged_role","displayName","Teste","password","UserTestPassword42!","role","ADMIN"));
        assertEquals(400,session.send("POST","/api/auth/register",attempted).statusCode());
        var regular=user();var me=regular.json("/api/auth/me");
        assertEquals("USER",me.path("user").path("role").asText());
        assertFalse(me.toString().contains("password"));
        var account=users.findByUsername(me.path("user").path("username").asText()).orElseThrow();
        assertNotEquals("UserTestPassword42!",account.getPasswordHash());
        assertTrue(encoder.matches("UserTestPassword42!",account.getPasswordHash()));
    }
    @Test void registrationRejectsDuplicatesAndInvalidPassword() throws Exception {
        var session=user();String username=session.json("/api/auth/me").path("user").path("username").asText();
        String body=mapper.writeValueAsString(Map.of("username",username.toUpperCase(Locale.ROOT),"displayName","Teste","password","UserTestPassword42!"));
        assertEquals(409,session.send("POST","/api/auth/register",body).statusCode());
        assertEquals(400,session.send("POST","/api/auth/register","{\"username\":\"short_user\",\"displayName\":\"Teste\",\"password\":\"short\"}").statusCode());
        String unicode=mapper.writeValueAsString(Map.of("username","unicode_password","displayName","Teste","password","á".repeat(40)));
        assertEquals(400,session.send("POST","/api/auth/register",unicode).statusCode());
    }
    @Test void ordinaryUserCannotCreateGamesEvenWithCsrf() throws Exception {
        assertEquals(403,user().send("POST","/api/games",gameJson).statusCode());
    }
    @Test void adminCanCreateGamesWithAutomaticId() throws Exception {
        var session=admin();var response=session.send("POST","/api/games",gameJson);
        assertEquals(201,response.statusCode(),response.body());
        var game=mapper.readTree(response.body());assertTrue(game.path("id").asLong()>=1000);
        assertEquals("PC, Xbox",game.path("platforms").asText());
        assertEquals("Jogo de teste",new Session().json("/api/games/"+game.path("id").asLong()).path("title").asText());
        assertTrue(response.headers().firstValue("Location").isPresent());
    }
    @Test void adminWritesStillRequireCsrfAndValidGameData() throws Exception {
        var session=admin();
        assertEquals(403,session.send("POST","/api/games",gameJson,false,"application/json").statusCode());
        assertEquals(400,session.send("POST","/api/games",gameJson.replace("[\"PC\",\"XBOX\"]","[]")).statusCode());
        assertEquals(400,session.send("POST","/api/games",gameJson.replace("[\"PC\",\"XBOX\"]","[\"XBOX\"]")).statusCode());
        assertEquals(400,session.send("POST","/api/games",gameJson.replace("2026","1500")).statusCode());
    }
    @Test void createsUpdatesAndRemovesOnlyOwnDiaryEntry() throws Exception {
        var first=user();var second=user();
        String body="{\"status\":\"PLAYING\",\"rating\":4,\"review\":\"Privado\",\"favorite\":true}";
        assertEquals(200,first.send("PUT","/api/diary/2",body).statusCode());
        assertEquals(0,second.json("/api/diary").size());
        assertEquals(200,second.send("PUT","/api/diary/2",body.replace("Privado","Segundo")).statusCode());
        assertEquals(1,first.json("/api/diary").size());
        assertEquals("Privado",first.json("/api/diary").get(0).path("review").asText());
        String updated="{\"status\":\"COMPLETED\",\"rating\":5,\"review\":\"Finalizado\",\"favorite\":false}";
        assertEquals(200,first.send("PUT","/api/diary/2",updated).statusCode());
        assertEquals(1,first.json("/api/diary").size());
        assertEquals(5,first.json("/api/diary").get(0).path("rating").asInt());
        assertEquals(204,second.send("DELETE","/api/diary/2",null).statusCode());
        assertEquals(0,second.json("/api/diary").size());
        assertEquals(1,first.json("/api/diary").size());
        assertEquals(204,first.send("DELETE","/api/diary/2",null).statusCode());
        assertEquals(0,first.json("/api/diary").size());
    }
    @Test void rejectsInvalidDiaryDataAndOwnerInjection() throws Exception {
        var session=user();
        assertEquals(400,session.send("PUT","/api/diary/3","{\"status\":\"PLAYING\",\"rating\":6}").statusCode());
        assertEquals(400,session.send("PUT","/api/diary/3","{\"rating\":3}").statusCode());
        assertEquals(400,session.send("PUT","/api/diary/3","{\"status\":\"INVALID\"}").statusCode());
        assertEquals(400,session.send("PUT","/api/diary/3","{\"status\":\"PLAYING\",\"userId\":1}").statusCode());
        assertEquals(400,session.send("PUT","/api/diary/3","{\"status\":\"PLAYING\",\"review\":\""+"a".repeat(2001)+"\"}").statusCode());
        assertEquals(403,session.send("PUT","/api/diary/3","{\"status\":\"PLAYING\"}",false,"application/json").statusCode());
    }
    @Test void rejectsMissingGameAndMalformedId() throws Exception {
        var session=user();
        assertEquals(404,session.send("GET","/api/games/9999",null).statusCode());
        assertEquals(404,session.send("GET","/api/games/9999/prices",null).statusCode());
        assertEquals(404,session.send("PUT","/api/diary/9999","{\"status\":\"PLAYING\"}").statusCode());
        assertEquals(400,session.send("GET","/api/games/invalid",null).statusCode());
    }
    @Test void pricesExplicitlyRequireConfiguration() throws Exception {
        var quote=user().json("/api/games/1/prices");
        assertEquals("NOT_CONFIGURED",quote.path("status").asText());
        assertTrue(quote.path("currentRetail").isNull());assertEquals("PC",quote.path("platform").asText());
    }
    @Test void loginRotatesSessionAndLogoutInvalidatesIt() throws Exception {
        var session=new Session();session.refreshCsrf();String before=session.sessionId();
        session.login("test_admin","AdminTestPassword42!");assertNotEquals(before,session.sessionId());
        assertEquals("ADMIN",session.json("/api/auth/me").path("user").path("role").asText());
        assertEquals(200,session.send("POST","/api/auth/logout",null).statusCode());
        assertFalse(session.json("/api/auth/me").path("authenticated").asBoolean());
        assertEquals(401,session.send("GET","/api/diary",null).statusCode());
    }
    @Test void badCredentialsDoNotAuthenticate() throws Exception {
        var session=new Session();session.refreshCsrf();
        assertEquals(401,session.send("POST","/api/auth/login","username=test_admin&password=wrong",true,"application/x-www-form-urlencoded").statusCode());
        assertFalse(session.json("/api/auth/me").path("authenticated").asBoolean());
    }
    String profileJson(String name,String email,String password,String avatar) throws Exception {
        var data=new LinkedHashMap<String,Object>();data.put("displayName",name);data.put("bio","Gosto de RPGs e boas histórias.");
        data.put("email",email);data.put("currentPassword",password);data.put("avatarUrl",avatar);return mapper.writeValueAsString(data);
    }
    String newGameJson(String title) throws Exception {
        return mapper.writeValueAsString(Map.of("title",title,"genre","Aventura","platforms",List.of("PC","PLAYSTATION"),
            "releaseYear",2026,"description","Descrição de teste para um jogo novo com vinte caracteres."));
    }
    String accountName(Session session) throws Exception {return session.json("/api/auth/me").path("user").path("username").asText();}
    @Test void profileCanBeEditedAndShowsOwnReviews() throws Exception {
        var first=user();var second=user();
        assertEquals(200,first.send("PUT","/api/profile",profileJson("Novo nome",null,null,null)).statusCode());
        assertEquals("Novo nome",first.json("/api/profile").path("user").path("displayName").asText());
        assertEquals(200,first.send("PUT","/api/diary/1","{\"status\":\"COMPLETED\",\"rating\":5,\"review\":\"Minha resenha pessoal\",\"favorite\":true,\"wishlist\":true}").statusCode());
        var profile=first.json("/api/profile");assertEquals(1,profile.path("reviews").size());
        assertEquals("Minha resenha pessoal",profile.path("reviews").get(0).path("entry").path("review").asText());
        assertEquals(1,profile.path("stats").path("wishlist").asInt());
        assertEquals("COMPLETED",first.json("/api/diary").get(0).path("status").asText());
        assertEquals(0,second.json("/api/profile").path("reviews").size());
        assertFalse(profile.toString().contains("passwordHash"));
    }
    @Test void emailChangeRequiresPasswordAndProfileRejectsRole() throws Exception {
        var session=user();String email=accountName(session)+"@example.test";
        assertEquals(400,session.send("PUT","/api/profile",profileJson("Pessoa",email,null,null)).statusCode());
        assertEquals(400,session.send("PUT","/api/profile",profileJson("Pessoa",email,"wrong",null)).statusCode());
        assertEquals(200,session.send("PUT","/api/profile",profileJson("Pessoa",email,"UserTestPassword42!",null)).statusCode());
        assertEquals(email,session.json("/api/profile").path("user").path("email").asText());
        var account=users.findByUsername(accountName(session)).orElseThrow();
        String token="b".repeat(43);resets.save(new PasswordResetToken(account.getId(),PasswordResetService.hash(token),Instant.now().plusSeconds(600)));
        assertEquals(200,session.send("PUT","/api/profile",profileJson("Pessoa","new_"+email,"UserTestPassword42!",null)).statusCode());
        assertEquals(400,session.send("POST","/api/auth/reset-password",mapper.writeValueAsString(Map.of("token",token,"newPassword","OtherPassword42!"))).statusCode());
        assertEquals(400,session.send("PUT","/api/profile","{\"displayName\":\"Pessoa\",\"role\":\"ADMIN\"}").statusCode());
    }
    @Test void customListsHaveOwnershipCrudAndNoDuplicateGames() throws Exception {
        var first=user();var second=user();
        var created=first.send("POST","/api/lists","{\"name\":\"Para jogar juntos\",\"description\":\"Cooperativos\"}");assertEquals(201,created.statusCode());
        long id=mapper.readTree(created.body()).path("id").asLong();
        assertEquals(404,second.send("GET","/api/lists/"+id,null).statusCode());
        assertEquals(404,second.send("PUT","/api/lists/"+id+"/games/1",null).statusCode());
        assertEquals(404,second.send("DELETE","/api/lists/"+id,null).statusCode());
        assertEquals(200,first.send("PUT","/api/lists/"+id+"/games/1",null).statusCode());
        assertEquals(200,first.send("PUT","/api/lists/"+id+"/games/1",null).statusCode());
        assertEquals(1,first.json("/api/lists/"+id).path("games").size());
        assertEquals(200,first.send("PUT","/api/lists/"+id,"{\"name\":\"Novo nome\",\"description\":\"Nova descrição\"}").statusCode());
        assertEquals("Novo nome",first.json("/api/lists/"+id).path("name").asText());
        assertEquals(204,first.send("DELETE","/api/lists/"+id+"/games/1",null).statusCode());
        assertEquals(0,first.json("/api/lists/"+id).path("games").size());
        assertEquals(204,first.send("DELETE","/api/lists/"+id,null).statusCode());
        assertEquals(0,first.json("/api/lists").size());
    }
    @Test void customListsRejectInvalidGamesAndForgery() throws Exception {
        var session=user();
        assertEquals(400,session.send("POST","/api/lists","{\"name\":\"Minha lista\",\"userId\":1}").statusCode());
        assertEquals(400,session.send("POST","/api/lists","{\"name\":\"\"}").statusCode());
        assertEquals(403,session.send("POST","/api/lists","{\"name\":\"Minha lista\"}",false,"application/json").statusCode());
        var response=session.send("POST","/api/lists","{\"name\":\"Minha lista\"}");
        long id=mapper.readTree(response.body()).path("id").asLong();
        assertEquals(404,session.send("PUT","/api/lists/"+id+"/games/9999",null).statusCode());
    }
    @Test void editingGameIsAdminOnlyAndPreservesDiary() throws Exception {
        var administrator=admin();var gamer=user();
        long id=mapper.readTree(administrator.send("POST","/api/games",newGameJson("Jogo para editar")).body()).path("id").asLong();
        assertEquals(200,gamer.send("PUT","/api/diary/"+id,"{\"status\":\"PLAYING\",\"review\":\"Continua salvo\"}").statusCode());
        assertEquals(403,gamer.send("PUT","/api/games/"+id,newGameJson("Alteração indevida")).statusCode());
        assertEquals(200,administrator.send("PUT","/api/games/"+id,newGameJson("Jogo editado")).statusCode());
        assertEquals("Jogo editado",gamer.json("/api/games/"+id).path("title").asText());
        assertEquals("Continua salvo",gamer.json("/api/diary").get(0).path("review").asText());
    }
    @Test void storeLinksMustMatchOfficialHostAndPlatform() throws Exception {
        var administrator=admin();var base=mapper.readTree(newGameJson("Jogo com links"));
        ((com.fasterxml.jackson.databind.node.ObjectNode)base).set("storeLinks",mapper.readTree("{\"playstation\":\"https://store.playstation.com/pt-br/product/example\"}"));
        assertEquals(201,administrator.send("POST","/api/games",base.toString()).statusCode());
        ((com.fasterxml.jackson.databind.node.ObjectNode)base).set("storeLinks",mapper.readTree("{\"playstation\":\"https://store.playstation.com.evil.test/game\"}"));
        assertEquals(400,administrator.send("POST","/api/games",base.toString()).statusCode());
        ((com.fasterxml.jackson.databind.node.ObjectNode)base).set("storeLinks",mapper.readTree("{\"xbox\":\"https://www.xbox.com/game\"}"));
        assertEquals(400,administrator.send("POST","/api/games",base.toString()).statusCode());
    }
    @Test void catalogImportIsAdminOnlyAndAtomic() throws Exception {
        var administrator=admin();var gamer=user();
        String body="{\"games\":["+newGameJson("Importado um")+","+newGameJson("Importado dois")+"]}";
        assertEquals(403,gamer.send("POST","/api/games/import",body).statusCode());
        assertEquals(201,administrator.send("POST","/api/games/import",body).statusCode());
        int before=administrator.json("/api/games").size();
        var invalid=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(newGameJson("Não deve ser importado"));
        invalid.put("steamAppId",1245620); // Já existe no catálogo inicial.
        String duplicate="{\"games\":["+newGameJson("Primeiro rollback")+","+invalid+"]}";
        assertEquals(409,administrator.send("POST","/api/games/import",duplicate).statusCode());
        assertEquals(before,administrator.json("/api/games").size());
        assertEquals(400,administrator.send("POST","/api/games/import","{\"games\":[]}").statusCode());
    }
    @Test void avatarUploadChecksFileContentAndOwnership() throws Exception {
        var first=user();var second=user();
        var image=new java.awt.image.BufferedImage(10,10,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
        var upload=first.upload(bytes.toByteArray(),"avatar.png","image/png");assertEquals(200,upload.statusCode(),upload.body());
        String url=mapper.readTree(upload.body()).path("url").asText();
        assertEquals(200,first.send("PUT","/api/profile",profileJson("Pessoa",null,null,url)).statusCode());
        assertEquals(url,first.json("/api/profile").path("user").path("avatarUrl").asText());
        assertEquals(400,second.send("PUT","/api/profile",profileJson("Pessoa",null,null,url)).statusCode());
        assertEquals(200,new Session().send("GET",url,null).statusCode());
        assertEquals(400,first.upload("<svg><script>alert(1)</script></svg>".getBytes(),"avatar.png","image/png").statusCode());
        assertEquals(400,first.send("PUT","/api/profile",profileJson("Pessoa",null,null,"/media/../../.env")).statusCode());
    }
    @Test void passwordChangeRevokesOtherSessionsAndRequiresCurrentPassword() throws Exception {
        var first=user();String username=accountName(first);var other=new Session();other.login(username,"UserTestPassword42!");
        assertEquals(400,first.send("PUT","/api/profile/password","{\"currentPassword\":\"wrong\",\"newPassword\":\"NewStrongPassword42!\"}").statusCode());
        assertEquals(200,first.send("PUT","/api/profile/password","{\"currentPassword\":\"UserTestPassword42!\",\"newPassword\":\"NewStrongPassword42!\"}").statusCode());
        assertEquals(401,other.send("GET","/api/diary",null).statusCode());
        assertFalse(first.json("/api/auth/me").path("authenticated").asBoolean());
        first.login(username,"NewStrongPassword42!");assertTrue(first.json("/api/auth/me").path("authenticated").asBoolean());
    }
    @Test void passwordRecoveryIsGenericSingleUseAndRevokesSessions() throws Exception {
        when(mail.enabled()).thenReturn(true);
        var session=user();String username=accountName(session),email=username+"@example.test";
        assertEquals(200,session.send("PUT","/api/profile",profileJson("Pessoa",email,"UserTestPassword42!",null)).statusCode());
        var anonymous=new Session();anonymous.refreshCsrf();
        var unknown=anonymous.send("POST","/api/auth/forgot-password","{\"username\":\"unknown_person\",\"email\":\"unknown@example.test\"}");
        var request=anonymous.send("POST","/api/auth/forgot-password",mapper.writeValueAsString(Map.of("username",username,"email",email)));
        assertEquals(200,request.statusCode());assertEquals(unknown.body(),request.body());
        var url=ArgumentCaptor.forClass(String.class);verify(mail).send(eq(email),url.capture());
        String token=url.getValue().split("#reset=")[1];assertFalse(request.body().contains(token));
        var saved=resets.findTopByUserIdOrderByIdDesc(users.findByUsername(username).orElseThrow().getId()).orElseThrow();
        assertFalse(saved.isUsed());
        var body=mapper.writeValueAsString(Map.of("token",token,"newPassword","RecoveredPassword42!"));
        assertEquals(200,anonymous.send("POST","/api/auth/reset-password",body).statusCode());
        assertEquals(400,anonymous.send("POST","/api/auth/reset-password",body).statusCode());
        assertEquals(401,session.send("GET","/api/diary",null).statusCode());
        session.login(username,"RecoveredPassword42!");assertTrue(session.json("/api/auth/me").path("authenticated").asBoolean());
    }
    @Test void recoveryRejectsExpiredTokenAndReportsDisabledMail() throws Exception {
        var session=user();var account=users.findByUsername(accountName(session)).orElseThrow();
        String token="a".repeat(43);resets.save(new PasswordResetToken(account.getId(),PasswordResetService.hash(token),Instant.now().minusSeconds(1)));
        var anonymous=new Session();anonymous.refreshCsrf();
        assertEquals(400,anonymous.send("POST","/api/auth/reset-password",mapper.writeValueAsString(Map.of("token",token,"newPassword","NewPasswordTest42!"))).statusCode());
        assertFalse(anonymous.json("/api/auth/recovery-status").path("enabled").asBoolean());
        assertEquals(503,anonymous.send("POST","/api/auth/forgot-password","{\"username\":\"some_user\",\"email\":\"user@example.test\"}").statusCode());
    }

}
