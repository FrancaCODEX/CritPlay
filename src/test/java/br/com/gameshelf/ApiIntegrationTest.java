package br.com.gameshelf;

import br.com.gameshelf.users.*;
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
    "app.admin.username=test_admin", "app.admin.password=AdminTestPassword42!"
})
class ApiIntegrationTest {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
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
}
