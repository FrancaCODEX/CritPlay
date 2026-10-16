package br.com.critplay;

import br.com.critplay.users.*;
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
    @Autowired br.com.critplay.metadata.ImportJobStore jobs;
    @MockitoBean ResetMailer mail;
    @MockitoBean br.com.critplay.steam.SteamResolver steam;
    @MockitoBean br.com.critplay.metadata.RawgClient rawg;
    static final java.nio.file.Path MEDIA=tempMedia();
    static java.nio.file.Path tempMedia(){try{return java.nio.file.Files.createTempDirectory("critplay-api-media-");}catch(java.io.IOException e){throw new IllegalStateException(e);}}
    @org.springframework.test.context.DynamicPropertySource
    static void mediaProperties(org.springframework.test.context.DynamicPropertyRegistry registry){registry.add("app.media.directory",()->MEDIA.toString());}
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
        session.login(username,"UserTestPassword42!");session.send("PUT","/api/profile/privacy",privacy("PRIVATE",true));return session;
    }
    Session admin() throws Exception {
        var session=new Session();session.login("test_admin","AdminTestPassword42!");return session;
    }
    @Autowired org.springframework.jdbc.core.JdbcTemplate reviewDb;
    @Test void gameReviewsRespectVisibilityBlocksAndModeration() throws Exception {
        var owner=user(); var friend=user(); var stranger=new Session();
        String ownerName=accountName(owner), friendName=accountName(friend);
        long ownerId=users.findByUsername(ownerName).orElseThrow().getId();
        long friendId=users.findByUsername(friendName).orElseThrow().getId();
        var created=admin().send("POST","/api/games",gameJson.replace("\"steamAppId\":420", "\"steamAppId\":null"));
        assertEquals(201,created.statusCode());
        long gameId=mapper.readTree(created.body()).path("id").asLong();
        String path="/api/games/"+gameId+"/reviews";
        assertEquals(200,owner.send("PUT","/api/diary/"+gameId,
            "{\"status\":\"COMPLETED\",\"rating\":5,\"review\":\"Muito bom\",\"favorite\":true}").statusCode());
        assertEquals(0,stranger.json(path).path("ratingCount").asInt());
        assertEquals(5,owner.json(path).path("averageRating").asDouble());
        reviewDb.update("UPDATE app_users SET visibility='PUBLIC',discoverable=TRUE WHERE id=?",ownerId);
        var publicReview=stranger.json(path);
        assertEquals(1,publicReview.path("ratingCount").asInt());
        assertEquals(ownerName,publicReview.path("reviews").get(0).path("username").asText());
        assertFalse(publicReview.path("reviews").get(0).has("favorite"));
        assertEquals(200,friend.send("PUT","/api/diary/"+gameId,
            "{\"status\":\"PLAYING\",\"rating\":3,\"review\":\"\",\"favorite\":false}").statusCode());
        reviewDb.update("UPDATE app_users SET visibility='PUBLIC',discoverable=TRUE WHERE id=?",friendId);
        assertEquals(4,stranger.json(path).path("averageRating").asDouble());
        assertEquals(1,stranger.json(path).path("reviewCount").asInt());
        reviewDb.update("UPDATE app_users SET visibility='FRIENDS',discoverable=FALSE WHERE id=?",ownerId);
        assertEquals(1,friend.json(path).path("ratingCount").asInt());
        reviewDb.update("INSERT INTO friendships(first_id,second_id,requester_id,status) VALUES (?,?,?,'ACCEPTED')",
            Math.min(ownerId,friendId),Math.max(ownerId,friendId),ownerId);
        assertEquals(2,friend.json(path).path("ratingCount").asInt());
        reviewDb.update("INSERT INTO user_blocks(blocker_id,blocked_id) VALUES (?,?)",ownerId,friendId);
        assertEquals(1,friend.json(path).path("ratingCount").asInt());
        reviewDb.update("DELETE FROM user_blocks WHERE blocker_id=?",ownerId);
        reviewDb.update("UPDATE diary_entries SET review_hidden=TRUE WHERE user_id=? AND game_id=?",ownerId,gameId);
        assertEquals(1,friend.json(path).path("ratingCount").asInt());
        reviewDb.update("UPDATE app_users SET enabled=FALSE WHERE id=?",friendId);
        assertEquals(0,stranger.json(path).path("ratingCount").asInt());
        assertTrue(stranger.json(path).path("averageRating").isNull());
        assertEquals(404,stranger.send("GET","/api/games/99999999/reviews",null).statusCode());
        assertEquals(200,stranger.send("GET","/favicon.png",null).statusCode());
        assertEquals(200,stranger.send("GET","/favicon.ico",null).statusCode());
    }
    long communityGame() throws Exception {
        var response=admin().send("POST","/api/games",gameJson.replace("\"steamAppId\":420","\"steamAppId\":null"));
        assertEquals(201,response.statusCode());return mapper.readTree(response.body()).path("id").asLong();
    }
    String diaryBody(double rating,String review,boolean favorite) throws Exception {
        return mapper.writeValueAsString(Map.of("status","PLAYING","rating",rating,"review",review,"favorite",favorite,"wishlist",false));
    }
    @Test void acceptsOnlyHalfStarsWithoutRoundingAndKeepsExistingRatings() throws Exception {
        var owner=user();long game=communityGame();String path="/api/diary/"+game;
        for(double rating:new double[]{0.5,1,1.5,4.5,5}) {
            var response=owner.send("PUT",path,diaryBody(rating,"Nota fracionada",false));
            assertEquals(200,response.statusCode());assertEquals(rating,mapper.readTree(response.body()).path("rating").asDouble());
        }
        for(double rating:new double[]{0,0.25,3.25,5.5,6})assertEquals(400,owner.send("PUT",path,diaryBody(rating,"Inválida",false)).statusCode());
        assertEquals(5,owner.json("/api/diary").get(0).path("rating").asDouble());
    }
    @Test void gamerIdentityValidatesOwnershipDuplicatesAndPrivacy() throws Exception {
        var owner=user();var other=user();String username=accountName(owner);
        byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAoAAAAKCAIAAAACUFjqAAAAFElEQVR4nGN0yzNiwA2Y8MiNYGkAIi0A+uIAXFoAAAAASUVORK5CYII=");
        var upload=owner.upload(png,"banner.png","image/png");assertEquals(200,upload.statusCode());String banner=mapper.readTree(upload.body()).path("url").asText();
        var otherUpload=other.upload(png,"other.png","image/png");String foreign=mapper.readTree(otherUpload.body()).path("url").asText();
        var games=owner.json("/api/games");var ids=new ArrayList<Long>();for(int i=0;i<4;i++)ids.add(games.get(i).path("id").asLong());
        String body=mapper.writeValueAsString(Map.of("bannerUrl",banner,"featuredGameIds",ids));
        assertEquals(200,owner.send("PUT","/api/profile/gamer",body).statusCode());
        var own=owner.json("/api/profile");assertEquals(banner,own.path("user").path("bannerUrl").asText());assertEquals(4,own.path("user").path("featuredGames").size());
        var hidden=other.json("/api/people/"+username);assertFalse(hidden.path("visible").asBoolean());assertFalse(hidden.path("user").has("bannerUrl"));
        assertEquals(400,owner.send("PUT","/api/profile/gamer",mapper.writeValueAsString(Map.of("bannerUrl",foreign,"featuredGameIds",ids))).statusCode());
        assertEquals(400,owner.send("PUT","/api/profile/gamer",mapper.writeValueAsString(Map.of("featuredGameIds",List.of(ids.get(0),ids.get(0))))).statusCode());
        assertEquals(400,owner.send("PUT","/api/profile/gamer",mapper.writeValueAsString(Map.of("featuredGameIds",List.of(1,2,3,4,5)))).statusCode());
        assertEquals(404,owner.send("PUT","/api/profile/gamer","{\"featuredGameIds\":[99999999]}").statusCode());
        assertEquals(200,owner.send("PUT","/api/profile/privacy","{\"visibility\":\"PUBLIC\",\"discoverable\":true}").statusCode());
        assertEquals(banner,new Session().json("/api/people/"+username).path("user").path("bannerUrl").asText());
        assertEquals(ids.get(0).longValue(),other.json("/api/people/"+username).path("user").path("featuredGames").get(0).path("id").asLong());
        assertEquals(200,owner.send("PUT","/api/profile/gamer","{\"bannerUrl\":null,\"featuredGameIds\":[]}").statusCode());
        assertTrue(owner.json("/api/profile").path("user").path("bannerUrl").isNull());
    }
    @Test void feedAndLikesRespectFollowsPrivacyBlocksModerationAndDeletion() throws Exception {
        var owner=user();var viewer=user();String name=accountName(owner);long game=communityGame();
        long ownerId=users.findByUsername(name).orElseThrow().getId();
        assertEquals(200,owner.send("PUT","/api/profile/privacy","{\"visibility\":\"PUBLIC\",\"discoverable\":true}").statusCode());
        assertEquals(200,owner.send("PUT","/api/diary/"+game,diaryBody(4.5,"Minha aventura",false)).statusCode());
        assertEquals(0,viewer.json("/api/feed").path("items").size());
        assertEquals(200,viewer.send("PUT","/api/people/"+name+"/follow",null).statusCode());
        var feed=viewer.json("/api/feed");assertEquals(1,feed.path("items").size());assertEquals("REVIEW",feed.path("items").get(0).path("action").asText());
        var review=viewer.json("/api/games/"+game+"/reviews").path("reviews").get(0);long entry=review.path("entryId").asLong();
        assertEquals(4.5,review.path("rating").asDouble());String like="/api/reviews/"+entry+"/like";
        for(int i=0;i<2;i++){var response=viewer.send("PUT",like,null);assertEquals(200,response.statusCode());assertEquals(1,mapper.readTree(response.body()).path("likeCount").asInt());}
        assertTrue(viewer.json("/api/games/"+game+"/reviews").path("reviews").get(0).path("likedByMe").asBoolean());
        assertEquals(400,owner.send("PUT",like,null).statusCode());
        assertEquals(200,viewer.send("DELETE",like,null).statusCode());assertEquals(200,viewer.send("DELETE",like,null).statusCode());
        assertEquals(200,viewer.send("PUT",like,null).statusCode());
        owner.send("PUT","/api/diary/"+game,diaryBody(4.5,"Minha aventura",true));
        assertEquals(1,viewer.json("/api/feed").path("items").size(),"Favoritar não deve gerar atividade privada");
        owner.send("PUT","/api/diary/"+game,diaryBody(4,"Minha aventura",true));
        var latest=viewer.json("/api/feed");assertEquals(2,latest.path("items").size());long cursor=latest.path("items").get(0).path("id").asLong();
        assertEquals(1,viewer.json("/api/feed?before="+cursor).path("items").size());
        owner.send("PUT","/api/profile/privacy","{\"visibility\":\"PRIVATE\",\"discoverable\":true}");
        assertEquals(0,viewer.json("/api/feed").path("items").size());assertEquals(404,viewer.send("PUT",like,null).statusCode());
        owner.send("PUT","/api/profile/privacy","{\"visibility\":\"PUBLIC\",\"discoverable\":true}");
        viewer.send("PUT","/api/blocks/"+name,null);assertEquals(0,viewer.json("/api/feed").path("items").size());assertEquals(404,viewer.send("PUT",like,null).statusCode());
        viewer.send("DELETE","/api/blocks/"+name,null);viewer.send("PUT","/api/people/"+name+"/follow",null);
        reviewDb.update("UPDATE diary_entries SET review_hidden=TRUE WHERE user_id=? AND game_id=?",ownerId,game);
        assertEquals(0,viewer.json("/api/feed").path("items").size());assertEquals(404,viewer.send("PUT",like,null).statusCode());
        reviewDb.update("UPDATE diary_entries SET review_hidden=FALSE WHERE user_id=? AND game_id=?",ownerId,game);
        assertEquals(204,owner.send("DELETE","/api/diary/"+game,null).statusCode());assertEquals(0,viewer.json("/api/feed").path("items").size());
        assertEquals(0,reviewDb.queryForObject("SELECT COUNT(*) FROM review_likes WHERE entry_id=?",Long.class,entry));
        assertEquals(404,viewer.send("PUT",like,null).statusCode());assertEquals(401,new Session().send("GET","/api/feed",null).statusCode());
    }
    @Test void homeAndCatalogArePublic() throws Exception {
        var session=new Session();
        assertEquals(200,session.send("GET","/",null).statusCode());
        assertEquals("UP",session.json("/api/health").path("status").asText());
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
    byte[] animatedGif(int frames) throws Exception {
  var bytes=new java.io.ByteArrayOutputStream();var writer=javax.imageio.ImageIO.getImageWritersByFormatName("gif").next();
  try(var out=javax.imageio.ImageIO.createImageOutputStream(bytes)){writer.setOutput(out);writer.prepareWriteSequence(null);
   for(int i=0;i<frames;i++){
    var image=new java.awt.image.BufferedImage(10,10,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(i==0?java.awt.Color.RED:java.awt.Color.BLUE);g.fillRect(0,0,10,10);g.dispose();
    var meta=writer.getDefaultImageMetadata(javax.imageio.ImageTypeSpecifier.createFromRenderedImage(image),null);var tree=(javax.imageio.metadata.IIOMetadataNode)meta.getAsTree("javax_imageio_gif_image_1.0");
    var control=(javax.imageio.metadata.IIOMetadataNode)tree.getElementsByTagName("GraphicControlExtension").item(0);control.setAttribute("delayTime","12");
    var comments=new javax.imageio.metadata.IIOMetadataNode("CommentExtensions");var comment=new javax.imageio.metadata.IIOMetadataNode("CommentExtension");comment.setAttribute("value","RemoveMe");comments.appendChild(comment);tree.appendChild(comments);
    if(i==0){var apps=new javax.imageio.metadata.IIOMetadataNode("ApplicationExtensions");var ext=new javax.imageio.metadata.IIOMetadataNode("ApplicationExtension");ext.setAttribute("applicationID","NETSCAPE");ext.setAttribute("authenticationCode","2.0");ext.setUserObject(new byte[]{1,0,0});apps.appendChild(ext);tree.appendChild(apps);}
    meta.setFromTree("javax_imageio_gif_image_1.0",tree);writer.writeToSequence(new javax.imageio.IIOImage(image,null,meta),null);
   }writer.endWriteSequence();
  }finally{writer.dispose();}
        return bytes.toByteArray();
    }
    @Test void gifAvatarAndBannerPreserveAnimationOwnershipAndCleanup() throws Exception {
        var owner=user();var other=user();var administrator=admin();byte[] gif=animatedGif(2);
        var response=owner.upload(gif,"profile.gif","image/gif");assertEquals(200,response.statusCode(),response.body());
        String url=mapper.readTree(response.body()).path("url").asText();assertTrue(url.endsWith(".gif"));
        var file=MEDIA.resolve(url.substring("/media/".length()));var poster=MEDIA.resolve(file.getFileName().toString().replace(".gif",".png"));
        assertTrue(java.nio.file.Files.exists(poster));
        try(var input=javax.imageio.ImageIO.createImageInputStream(file.toFile())) {
            var reader=javax.imageio.ImageIO.getImageReaders(input).next();reader.setInput(input,false,false);
            try {
                assertEquals(2,reader.getNumImages(true));assertNotEquals(reader.read(0).getRGB(0,0),reader.read(1).getRGB(0,0));
                var metadata=(javax.imageio.metadata.IIOMetadataNode)reader.getImageMetadata(0).getAsTree("javax_imageio_gif_image_1.0");
                assertEquals("12",((org.w3c.dom.Element)metadata.getElementsByTagName("GraphicControlExtension").item(0)).getAttribute("delayTime"));
                assertEquals(1,metadata.getElementsByTagName("ApplicationExtension").getLength());assertEquals(0,metadata.getElementsByTagName("CommentExtensions").getLength());
            } finally {reader.dispose();}
        }
        assertFalse(java.nio.file.Files.readString(file,java.nio.charset.StandardCharsets.ISO_8859_1).contains("RemoveMe"));
        assertEquals(200,owner.send("PUT","/api/profile",profileJson("Pessoa GIF",null,null,url)).statusCode());
        assertEquals(url,owner.json("/api/auth/me").path("user").path("avatarUrl").asText());
        assertEquals(200,owner.send("PUT","/api/profile/gamer",mapper.writeValueAsString(Map.of("bannerUrl",url,"featuredGameIds",List.of()))).statusCode());
        assertEquals(400,other.send("PUT","/api/profile/gamer",mapper.writeValueAsString(Map.of("bannerUrl",url,"featuredGameIds",List.of()))).statusCode());
        var media=new Session().send("GET",url,null);assertEquals(200,media.statusCode());assertTrue(media.headers().firstValue("Content-Type").orElse("").startsWith("image/gif"));
        assertEquals(200,new Session().send("GET",url.replace(".gif",".png"),null).statusCode());
        var stale=java.nio.file.attribute.FileTime.from(Instant.now().minusSeconds(172800));java.nio.file.Files.setLastModifiedTime(file,stale);java.nio.file.Files.setLastModifiedTime(poster,stale);
        administrator.send("POST","/api/admin/images/cleanup","{\"confirm\":true}");assertTrue(java.nio.file.Files.exists(file));assertTrue(java.nio.file.Files.exists(poster));
        owner.send("PUT","/api/profile",profileJson("Pessoa GIF",null,null,null));
        administrator.send("POST","/api/admin/images/cleanup","{\"confirm\":true}");assertTrue(java.nio.file.Files.exists(file));assertTrue(java.nio.file.Files.exists(poster));
        owner.send("PUT","/api/profile/gamer","{\"bannerUrl\":null,\"featuredGameIds\":[]}");
        administrator.send("POST","/api/admin/images/cleanup","{\"confirm\":true}");assertFalse(java.nio.file.Files.exists(file));assertFalse(java.nio.file.Files.exists(poster));
    }
    @Test void gifRejectsInvalidDataExcessiveFramesAndCanvasBudget() throws Exception {
        var owner=user();
        assertEquals(400,owner.upload("GIF89a-invalid".getBytes(),"invalid.gif","image/gif").statusCode());
        assertEquals(400,owner.upload(animatedGif(121),"frames.gif","image/gif").statusCode());
        byte[] oversized=animatedGif(2);oversized[6]=(byte)0xff;oversized[7]=(byte)0xff;
        assertEquals(400,owner.upload(oversized,"wide.gif","image/gif").statusCode());
        byte[] budget=animatedGif(16);budget[6]=(byte)0xd0;budget[7]=7;budget[8]=(byte)0xd0;budget[9]=7;
        assertEquals(400,owner.upload(budget,"budget.gif","image/gif").statusCode());
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

    String privacy(String visibility,boolean discoverable) {return "{\"visibility\":\""+visibility+"\",\"discoverable\":"+discoverable+"}";}
    @Test void followingDoesNotUnlockFriendsProfileAndOnlyRecipientCanAccept() throws Exception {
        var owner=user();var reader=user();var outsider=user();String target=accountName(owner),viewer=accountName(reader);
        assertEquals(200,owner.send("PUT","/api/profile/privacy",privacy("FRIENDS",true)).statusCode());
        assertEquals(200,owner.send("PUT","/api/diary/1","{\"status\":\"COMPLETED\",\"rating\":5,\"review\":\"Minha resenha reservada.\",\"favorite\":true,\"wishlist\":true}").statusCode());
        assertFalse(reader.json("/api/people/"+target).path("visible").asBoolean());
        assertEquals(200,reader.send("PUT","/api/people/"+target+"/follow",null).statusCode());
        assertEquals(200,reader.send("PUT","/api/people/"+target+"/follow",null).statusCode());
        assertFalse(reader.json("/api/people/"+target).path("visible").asBoolean());
        assertEquals(200,reader.send("PUT","/api/people/"+target+"/friendship","{\"action\":\"REQUEST\"}").statusCode());
        assertEquals(400,reader.send("PUT","/api/people/"+target+"/friendship","{\"action\":\"ACCEPT\"}").statusCode());
        assertEquals(400,outsider.send("PUT","/api/people/"+target+"/friendship","{\"action\":\"ACCEPT\"}").statusCode());
        assertEquals(1,owner.json("/api/social").path("incoming").size());
        assertEquals(200,owner.send("PUT","/api/people/"+viewer+"/friendship","{\"action\":\"ACCEPT\"}").statusCode());
        assertEquals(target,reader.json("/api/social").path("friends").get(0).path("username").asText());
        var profile=reader.json("/api/people/"+target);assertTrue(profile.path("visible").asBoolean());
        assertEquals("Minha resenha reservada.",profile.path("reviews").get(0).path("review").asText());
        assertFalse(profile.path("user").has("email"));assertFalse(profile.path("user").has("passwordHash"));assertFalse(profile.path("reviews").get(0).has("entry"));
        assertEquals(200,reader.send("PUT","/api/people/"+target+"/friendship","{\"action\":\"REMOVE\"}").statusCode());
        assertFalse(reader.json("/api/people/"+target).path("visible").asBoolean());
        assertEquals(1,owner.json("/api/social").path("followers").size());
        assertEquals(viewer,owner.json("/api/social").path("followers").get(0).path("username").asText());
    }
    @Test void sharedCollectionsRespectEachSwitchAndProfilePrivacy() throws Exception {
        var owner=user();String name=accountName(owner);var visitor=new Session();
        owner.send("PUT","/api/diary/1","{\"status\":\"PLAYING\",\"rating\":4.5,\"review\":\"Uma resenha\",\"favorite\":true,\"wishlist\":true}");
        var list=mapper.readTree(owner.send("POST","/api/lists","{\"name\":\"Minha coleção\"}").body());
        owner.send("PUT","/api/lists/"+list.path("id").asLong()+"/games/1",null);
        String settings="{\"visibility\":\"PUBLIC\",\"discoverable\":true,\"libraryPublic\":true,\"favoritesPublic\":true,\"wishlistPublic\":true,\"listsPublic\":true}";
        assertEquals(200,owner.send("PUT","/api/profile/privacy",settings).statusCode());
        var profile=visitor.json("/api/people/"+name);
        for(String section:List.of("library","favorites","wishlist","lists"))assertEquals(1,profile.path("collections").path(section).size());
        assertEquals("PLAYING",profile.path("collections").path("library").get(0).path("status").asText());
        assertFalse(profile.path("collections").toString().contains("Uma resenha"));
        assertFalse(profile.path("collections").toString().contains("userId"));
        for(String section:List.of("library","favorites","wishlist","lists")) {
            owner.send("PUT","/api/profile/privacy",settings.replace("\""+section+"Public\":true","\""+section+"Public\":false"));
            var hidden=visitor.json("/api/people/"+name);assertFalse(hidden.path("collections").has(section));
            assertEquals(3,hidden.path("collections").size());
            if(!section.equals("lists"))assertFalse(hidden.path("stats").has(section));
            assertTrue(owner.json("/api/people/"+name).path("collections").has(section));
        }
        owner.send("PUT","/api/profile/privacy",settings.replace("PUBLIC","FRIENDS"));
        assertFalse(visitor.json("/api/people/"+name).has("collections"));
        owner.send("PUT","/api/profile/privacy",settings.replace("PUBLIC","PRIVATE"));
        assertFalse(visitor.json("/api/people/"+name).has("collections"));
        owner.send("PUT","/api/profile/privacy",settings);
        var reader=user();owner.send("PUT","/api/blocks/"+accountName(reader),null);
        assertEquals(404,reader.send("GET","/api/people/"+name,null).statusCode());
    }
    @Test void registrationStartsPublicWithShareableCollections() throws Exception {
        var session=new Session();session.refreshCsrf();String name="public_"+UUID.randomUUID().toString().replace("-", "").substring(0,12);
        assertEquals(201,session.send("POST","/api/auth/register",mapper.writeValueAsString(Map.of("username",name,"displayName","Novo jogador","password","UserTestPassword42!"))).statusCode());
        session.login(name,"UserTestPassword42!");var user=session.json("/api/profile").path("user");
        assertEquals("PUBLIC",user.path("visibility").asText());assertTrue(user.path("discoverable").asBoolean());
        for(String section:List.of("library","favorites","wishlist","lists"))assertTrue(user.path("collectionPrivacy").path(section).asBoolean());
        assertTrue(new Session().json("/api/people/"+name).path("visible").asBoolean());
    }
    @Test void profileFollowListsAndCountsFilterHiddenBlockedAndSuspendedMembers() throws Exception {
        var owner=user();var first=user();var reader=user();String target=accountName(owner),a=accountName(first),b=accountName(reader);
        owner.send("PUT","/api/profile/privacy",privacy("PUBLIC",true));
        first.send("PUT","/api/people/"+target+"/follow",null);reader.send("PUT","/api/people/"+target+"/follow",null);owner.send("PUT","/api/people/"+b+"/follow",null);
        var visitor=new Session();String path="/api/people/"+target;
        assertEquals(2,owner.json("/api/profile").path("social").path("followers").asInt());assertEquals(1,owner.json("/api/profile").path("social").path("following").asInt());
        assertEquals(2,visitor.json(path+"/followers").path("total").asInt());assertEquals(b,visitor.json(path+"/following").path("items").get(0).path("username").asText());
        var person=visitor.json(path+"/followers").path("items").get(0);assertFalse(person.has("email"));assertFalse(person.has("role"));assertFalse(person.has("bio"));
        first.send("PUT","/api/profile/privacy",privacy("PRIVATE",false));
        assertEquals(1,visitor.json(path+"/followers").path("total").asInt());assertEquals(2,owner.json(path+"/followers").path("total").asInt());
        first.send("PUT","/api/profile/privacy",privacy("PRIVATE",true));reader.send("PUT","/api/blocks/"+a,null);
        assertEquals(1,reader.json(path+"/followers").path("total").asInt());assertEquals(1,reader.json(path).path("social").path("followers").asInt());
        reviewDb.update("UPDATE app_users SET enabled=FALSE WHERE username=?",a);
        assertEquals(1,owner.json(path+"/followers").path("total").asInt());
    }
    @Test void followListsHonorOwnerPrivacyDiscoveryFriendshipAndBlocks() throws Exception {
        var owner=user();var reader=user();String target=accountName(owner),viewer=accountName(reader);String path="/api/people/"+target+"/followers";
        assertEquals(404,new Session().send("GET",path,null).statusCode());assertEquals(200,owner.send("GET",path,null).statusCode());
        owner.send("PUT","/api/profile/privacy",privacy("FRIENDS",true));assertEquals(404,reader.send("GET",path,null).statusCode());
        reader.send("PUT","/api/people/"+target+"/friendship","{\"action\":\"REQUEST\"}");owner.send("PUT","/api/people/"+viewer+"/friendship","{\"action\":\"ACCEPT\"}");assertEquals(200,reader.send("GET",path,null).statusCode());
        owner.send("PUT","/api/profile/privacy",privacy("PUBLIC",false));assertEquals(404,new Session().send("GET",path,null).statusCode());assertEquals(200,reader.send("GET",path,null).statusCode());
        owner.send("PUT","/api/blocks/"+viewer,null);assertEquals(404,reader.send("GET",path,null).statusCode());
        reviewDb.update("UPDATE app_users SET enabled=FALSE WHERE username=?",target);assertEquals(404,new Session().send("GET",path,null).statusCode());
    }
    @Test void followListsUseCursorPaginationWithoutDuplicates() throws Exception {
        var owner=user();String target=accountName(owner);owner.send("PUT","/api/profile/privacy",privacy("PUBLIC",true));
        long id=users.findByUsername(target).orElseThrow().getId();
        for(int i=0;i<31;i++){var member=users.saveAndFlush(new AppUser("connection_"+UUID.randomUUID().toString().replace("-","").substring(0,12),"Conexão de teste","unused",Role.USER));reviewDb.update("INSERT INTO user_follows(follower_id,followed_id) VALUES (?,?)",member.getId(),id);}
        var visitor=new Session();String path="/api/people/"+target+"/followers";var first=visitor.json(path);assertEquals(31,first.path("total").asInt());assertEquals(30,first.path("items").size());
        var second=visitor.json(path+"?after="+first.path("nextCursor").asLong());assertEquals(1,second.path("items").size());assertTrue(second.path("nextCursor").isNull());
        var ids=new HashSet<Long>();for(var item:first.path("items"))ids.add(item.path("id").asLong());assertTrue(ids.add(second.path("items").get(0).path("id").asLong()));assertEquals(31,ids.size());
        assertEquals(400,visitor.send("GET",path+"?after=-1",null).statusCode());
    }
    @Test void privacySearchAndPublicSharingNeverExposeEmail() throws Exception {
        var owner=user();var reader=user();String target=accountName(owner);
        assertEquals(200,owner.send("PUT","/api/profile",profileJson("Pessoa pública",target+"@example.test","UserTestPassword42!",null)).statusCode());
        assertEquals(200,owner.send("PUT","/api/profile/privacy",privacy("PUBLIC",true)).statusCode());
        var anonymous=new Session();var profile=anonymous.json("/api/people/"+target);assertTrue(profile.path("visible").asBoolean());
        assertFalse(profile.toString().contains("@example.test"));
        assertEquals(1,reader.json("/api/people?q="+target).size());
        assertEquals(200,owner.send("PUT","/api/profile/privacy",privacy("PRIVATE",false)).statusCode());
        assertEquals(0,reader.json("/api/people?q="+target).size());
        assertEquals(404,reader.send("GET","/api/people/"+target,null).statusCode());
        assertEquals(404,anonymous.send("GET","/api/people/"+target,null).statusCode());
        assertEquals(401,anonymous.send("GET","/api/people?q=pe",null).statusCode());
        assertEquals(403,owner.send("PUT","/api/profile/privacy",privacy("PUBLIC",true),false,"application/json").statusCode());
    }
    @Test void friendshipCancellationRejectionAndSelfRequestsAreControlled() throws Exception {
        var first=user();var second=user();String a=accountName(first),b=accountName(second);
        assertEquals(400,first.send("PUT","/api/people/"+a+"/friendship","{\"action\":\"REQUEST\"}").statusCode());
        assertEquals(200,first.send("PUT","/api/people/"+b+"/friendship","{\"action\":\"REQUEST\"}").statusCode());
        assertEquals(400,second.send("PUT","/api/people/"+a+"/friendship","{\"action\":\"REQUEST\"}").statusCode());
        assertEquals(200,second.send("PUT","/api/people/"+a+"/friendship","{\"action\":\"REJECT\"}").statusCode());
        assertEquals(0,first.json("/api/social").path("outgoing").size());
        assertEquals(200,first.send("PUT","/api/people/"+b+"/friendship","{\"action\":\"REQUEST\"}").statusCode());
        assertEquals(200,first.send("PUT","/api/people/"+b+"/friendship","{\"action\":\"CANCEL\"}").statusCode());
        assertEquals(0,second.json("/api/social").path("incoming").size());
        assertEquals(200,first.send("PUT","/api/people/"+b+"/follow",null).statusCode());
        assertEquals(200,second.send("PUT","/api/profile/privacy",privacy("PRIVATE",false)).statusCode());
        assertEquals(200,first.send("DELETE","/api/people/"+b+"/follow",null).statusCode());
    }

    @Test void accountManagementIsAdminOnlyRevokesSessionsAndSuspensionBlocksLogin() throws Exception {
        var administrator=admin();var member=user();String username=accountName(member);long id=users.findByUsername(username).orElseThrow().getId();
        assertEquals(403,member.send("GET","/api/admin/accounts",null).statusCode());
        assertEquals(403,member.send("PUT","/api/admin/accounts/"+id,"{\"role\":\"ADMIN\",\"enabled\":true}").statusCode());
        var promoted=administrator.send("PUT","/api/admin/accounts/"+id,"{\"role\":\"ADMIN\",\"enabled\":true}");assertEquals(200,promoted.statusCode());
        assertFalse(promoted.body().contains("passwordHash"));assertFalse(promoted.body().contains("email"));
        assertEquals(401,member.send("GET","/api/diary",null).statusCode());
        member.login(username,"UserTestPassword42!");assertEquals(200,member.send("GET","/api/admin/accounts",null).statusCode());
        assertEquals(200,administrator.send("PUT","/api/admin/accounts/"+id,"{\"role\":\"USER\",\"enabled\":false}").statusCode());
        assertEquals(401,member.send("GET","/api/diary",null).statusCode());member.refreshCsrf();
        assertEquals(401,member.send("POST","/api/auth/login","username="+username+"&password=UserTestPassword42%21",true,"application/x-www-form-urlencoded").statusCode());
        assertEquals(0,administrator.json("/api/people?q="+username).size());
        assertEquals(404,new Session().send("GET","/api/people/"+username,null).statusCode());
        assertEquals(200,administrator.send("PUT","/api/admin/accounts/"+id,"{\"role\":\"USER\",\"enabled\":true}").statusCode());
        member.login(username,"UserTestPassword42!");assertEquals(403,member.send("GET","/api/admin/accounts",null).statusCode());
        long own=users.findByUsername("test_admin").orElseThrow().getId();
        assertEquals(400,administrator.send("PUT","/api/admin/accounts/"+own,"{\"role\":\"USER\",\"enabled\":true}").statusCode());
        assertEquals(400,administrator.send("PUT","/api/admin/accounts/"+own,"{\"role\":\"ADMIN\",\"enabled\":false}").statusCode());
        assertEquals(403,administrator.send("PUT","/api/admin/accounts/"+id,"{\"role\":\"ADMIN\",\"enabled\":true}",false,"application/json").statusCode());
    }
    String uploadFixture(Session session) throws Exception {
        var image=new java.awt.image.BufferedImage(8,8,java.awt.image.BufferedImage.TYPE_INT_RGB);var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
        var response=session.upload(bytes.toByteArray(),"cover.png","image/png");assertEquals(200,response.statusCode());return mapper.readTree(response.body()).path("url").asText();
    }
    @Test void cleanupPreservesReferencedRecentAndUnmanagedFilesAndRechecksPreview() throws Exception {
        var administrator=admin();var member=user();
        String avatar=uploadFixture(member),cover=uploadFixture(administrator),orphan=uploadFixture(member),recent=uploadFixture(member),newlyLinked=uploadFixture(member);
        assertEquals(200,member.send("PUT","/api/profile",profileJson("Pessoa",null,null,avatar)).statusCode());
        var game=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(newGameJson("Capa protegida"));game.put("coverUrl",cover);
        assertEquals(201,administrator.send("POST","/api/games",game.toString()).statusCode());
        for(String url:List.of(avatar,cover,orphan,newlyLinked))java.nio.file.Files.setLastModifiedTime(MEDIA.resolve(url.substring(7)),java.nio.file.attribute.FileTime.from(Instant.now().minusSeconds(30*3600)));
        var unmanaged=MEDIA.resolve("nao-gerenciado.png");java.nio.file.Files.writeString(unmanaged,"manter");java.nio.file.Files.setLastModifiedTime(unmanaged,java.nio.file.attribute.FileTime.from(Instant.now().minusSeconds(30*3600)));
        assertEquals(403,member.send("GET","/api/admin/images/cleanup",null).statusCode());
        assertEquals(2,administrator.json("/api/admin/images/cleanup").path("count").asInt());
        // Uma imagem candidata na prévia torna-se avatar antes da execução.
        assertEquals(200,member.send("PUT","/api/profile",profileJson("Pessoa",null,null,newlyLinked)).statusCode());
        // A antiga foto deixa de ser usada e volta ao conjunto elegível.
        assertEquals(400,administrator.send("POST","/api/admin/images/cleanup","{\"confirm\":false}").statusCode());
        var result=administrator.send("POST","/api/admin/images/cleanup","{\"confirm\":true}");assertEquals(200,result.statusCode());
        assertFalse(java.nio.file.Files.exists(MEDIA.resolve(orphan.substring(7))));
        for(String url:List.of(cover,recent,newlyLinked))assertTrue(java.nio.file.Files.exists(MEDIA.resolve(url.substring(7))));
        assertTrue(java.nio.file.Files.exists(unmanaged));
    }
    @Test void metadataImportRequiresAdminPreservesExistingDiaryAndRejectsDuplicateSource() throws Exception {
        var administrator=admin();var member=user();
        var metadata=new br.com.critplay.metadata.MetadataGame(991L,"fixture-game","Metadados fictícios","Aventura",Set.of(br.com.critplay.catalog.Platform.PC),2026,"Descrição fictícia de importação para teste.","https://media.rawg.io/media/test.jpg");
        when(rawg.detail(991L)).thenReturn(metadata);
        String request="{\"rawgId\":991,\"game\":"+newGameJson("Importação RAWG de teste")+"}";
        assertEquals(403,member.send("POST","/api/admin/metadata/import",request).statusCode());
        var created=administrator.send("POST","/api/admin/metadata/import",request);assertEquals(200,created.statusCode(),created.body());
        long id=mapper.readTree(created.body()).path("id").asLong();assertEquals(991,mapper.readTree(created.body()).path("rawgId").asLong());
        assertEquals("https://rawg.io/games/fixture-game",mapper.readTree(created.body()).path("metadataSourceUrl").asText());
        assertEquals(409,administrator.send("POST","/api/admin/metadata/import",request).statusCode());
        assertEquals(200,member.send("PUT","/api/diary/"+id,"{\"status\":\"PLAYING\",\"review\":\"Resenha preservada na importação.\",\"favorite\":false}").statusCode());
        String update="{\"rawgId\":991,\"targetGameId\":"+id+",\"game\":"+newGameJson("Importação revisada")+"}";
        assertEquals(200,administrator.send("POST","/api/admin/metadata/import",update).statusCode());
        assertEquals("Resenha preservada na importação.",member.json("/api/diary").get(0).path("review").asText());
        assertEquals("Importação revisada",member.json("/api/games/"+id).path("title").asText());
        assertEquals(403,administrator.send("POST","/api/admin/metadata/import",update,false,"application/json").statusCode());
    }

    @Test void steamAssociationRequiresAdminPreservesIdAndRejectsDuplicate() throws Exception {
        var administrator=admin();var member=user();
        assertEquals(200,new Session().send("GET","/steam.js",null).statusCode());
        String title="Steam association fixture";
        when(steam.resolve(title)).thenReturn(new br.com.critplay.steam.SteamResolver.Result("FOUND",777123L,"Encontrado"));
        long id=mapper.readTree(administrator.send("POST","/api/games",newGameJson(title)).body()).path("id").asLong();
        assertEquals(403,member.send("POST","/api/admin/steam/games/"+id,null).statusCode());
        assertEquals(403,administrator.send("POST","/api/admin/steam/games/"+id,null,false,"application/json").statusCode());
        assertEquals(200,member.send("PUT","/api/diary/"+id,"{\"status\":\"PLAYING\",\"review\":\"Resenha deve permanecer intacta.\"}").statusCode());
        assertEquals("FOUND",mapper.readTree(administrator.send("POST","/api/admin/steam/games/"+id,null).body()).path("status").asText());
        assertEquals(777123L,administrator.json("/api/games/"+id).path("steamAppId").asLong());
        assertEquals("PRESERVED",mapper.readTree(administrator.send("POST","/api/admin/steam/games/"+id,null).body()).path("status").asText());
        long other=mapper.readTree(administrator.send("POST","/api/games",newGameJson(title)).body()).path("id").asLong();
        assertEquals("CONFLICT",mapper.readTree(administrator.send("POST","/api/admin/steam/games/"+other,null).body()).path("status").asText());
        assertTrue(administrator.json("/api/games/"+other).path("steamAppId").isNull());
        assertEquals("Resenha deve permanecer intacta.",member.json("/api/diary").get(0).path("review").asText());
        verify(steam,times(2)).resolve(title);
    }

    @Test void diaryDatesArePersistedAndInvalidOrderOrFutureAreRejected() throws Exception {
        var member=user();String body="{\"status\":\"COMPLETED\",\"startDate\":\"2026-01-01\",\"finishDate\":\"2026-02-01\"}";
        assertEquals(200,member.send("PUT","/api/diary/1",body).statusCode());assertEquals("2026-02-01",member.json("/api/diary").get(0).path("finishDate").asText());
        assertEquals(400,member.send("PUT","/api/diary/1",body.replace("2026-02-01","2025-12-01")).statusCode());
        assertEquals(400,member.send("PUT","/api/diary/1","{\"status\":\"COMPLETED\",\"finishDate\":\"2026-02-01\"}").statusCode());
        assertEquals(400,member.send("PUT","/api/diary/1",body.replace("2026-02-01",java.time.LocalDate.now().plusDays(2).toString())).statusCode());
    }
    @Test void blockingHidesBothProfilesAndSearchAndRemovesConnections() throws Exception {
        var first=user();var second=user();String a=accountName(first),b=accountName(second);
        assertEquals(200,first.send("PUT","/api/people/"+b+"/follow",null).statusCode());
        assertEquals(200,first.send("PUT","/api/people/"+b+"/friendship","{\"action\":\"REQUEST\"}").statusCode());
        assertEquals(200,second.send("PUT","/api/people/"+a+"/friendship","{\"action\":\"ACCEPT\"}").statusCode());
        assertEquals(200,first.send("PUT","/api/blocks/"+b,null).statusCode());
        assertEquals(404,first.send("GET","/api/people/"+b,null).statusCode());assertEquals(404,second.send("GET","/api/people/"+a,null).statusCode());
        assertEquals(0,second.json("/api/people?q="+a).size());assertEquals(0,first.json("/api/social").path("friends").size());assertEquals(0,second.json("/api/social").path("followers").size());
        assertEquals(404,second.send("PUT","/api/people/"+a+"/follow",null).statusCode());assertEquals(1,first.json("/api/blocks").size());
        assertEquals(200,first.send("DELETE","/api/blocks/"+b,null).statusCode());assertEquals(1,second.json("/api/people?q="+a).size());assertEquals(0,first.json("/api/social").path("friends").size());
    }
    @Test void moderationHidesPublicReviewAndAuthorCannotBypassItByEditing() throws Exception {
        var owner=user();var reporter=user();var administrator=admin();String username=accountName(owner);
        owner.send("PUT","/api/profile/privacy",privacy("PUBLIC",true));owner.send("PUT","/api/diary/1","{\"status\":\"PLAYING\",\"review\":\"Conteúdo fictício denunciado para teste.\"}");
        String report=mapper.writeValueAsString(Map.of("username",username,"gameId",1,"reason","Denúncia fictícia para testar moderação."));
        assertEquals(200,reporter.send("POST","/api/reports",report).statusCode());assertEquals(409,reporter.send("POST","/api/reports",report).statusCode());
        assertEquals(403,reporter.send("GET","/api/admin/reports",null).statusCode());
        long id=administrator.json("/api/admin/reports").get(0).path("id").asLong();
        assertEquals(200,administrator.send("PUT","/api/admin/reports/"+id,"{\"status\":\"RESOLVED\",\"hideReview\":true}").statusCode());
        assertEquals(0,new Session().json("/api/people/"+username).path("reviews").size());
        owner.send("PUT","/api/diary/1","{\"status\":\"PLAYING\",\"review\":\"Uma edição continua aguardando revisão.\"}");
        assertTrue(owner.json("/api/diary").get(0).path("reviewHidden").asBoolean());
        owner.send("DELETE","/api/diary/1",null);owner.send("PUT","/api/diary/1","{\"status\":\"PLAYING\",\"review\":\"Recriar também não contorna a moderação.\"}");
        assertTrue(owner.json("/api/diary").get(0).path("reviewHidden").asBoolean());
        assertEquals(200,administrator.send("PUT","/api/admin/reports/"+id,"{\"status\":\"RESOLVED\",\"hideReview\":false}").statusCode());
        assertEquals(1,new Session().json("/api/people/"+username).path("reviews").size());
    }
    @Test void bulkImportPersistsFiveHundredGamesAndResumeDoesNotDuplicate() throws Exception {
        long owner=users.findByUsername("test_admin").orElseThrow().getId();var job=jobs.create(owner,500);
        var first=new br.com.critplay.metadata.MetadataGame(900001L,"bulk-fixture-1","Bulk fixture 1","Adventure",Set.of(br.com.critplay.catalog.Platform.PC),2026,"Descrição fictícia para verificar importação em lote.",null);
        jobs.consume(job,first,40,8899123L);jobs.consume(jobs.get(job.id()),first,40);jobs.consume(jobs.get(job.id()),null,40);
        jobs.pause(job.id(),"Pausa de teste");int saved=jobs.get(job.id()).processed();jobs.consume(jobs.get(job.id()),first,40);assertEquals(saved,jobs.get(job.id()).processed());jobs.resume(job.id(),owner);
        for(int i=2;i<=500;i++)jobs.consume(jobs.get(job.id()),new br.com.critplay.metadata.MetadataGame(900000L+i,"bulk-fixture-"+i,"Bulk fixture "+i,"Adventure",Set.of(br.com.critplay.catalog.Platform.PC),2026,"Descrição fictícia para verificar importação em lote.",null),40);
        var result=jobs.get(job.id());assertEquals(500,result.added());assertEquals(1,result.existing());assertEquals(1,result.skipped());assertEquals("COMPLETED",result.status());
        assertEquals(500,new Session().json("/api/games?q=Bulk%20fixture&genre=Aventura").size());
    }
    @Test void importJobsRequireAdminAndConfiguredKey() throws Exception {
        var administrator=admin();var member=user();
        assertEquals(403,member.send("POST","/api/admin/import-jobs","{\"count\":500}").statusCode());
        assertEquals(503,administrator.send("POST","/api/admin/import-jobs","{\"count\":500}").statusCode());
        assertEquals(400,administrator.send("POST","/api/admin/import-jobs","{\"count\":100}").statusCode());
    }

}
