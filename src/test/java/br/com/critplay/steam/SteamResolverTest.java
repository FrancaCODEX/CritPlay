package br.com.critplay.steam;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SteamResolverTest {
    private final ObjectMapper mapper=new ObjectMapper();
    @Test void exactTitleExcludesSequelAndSoundtrack() throws Exception {
        var result=SteamResolver.candidate(mapper.readTree("""
            {"items":[{"id":1145350,"name":"Hades II","type":"app"},{"id":1145360,"name":"Hades","type":"app"},{"id":4,"name":"Hades Soundtrack","type":"app"}]}
            """),"Hades");
        assertEquals(1145360L,result.appId());
    }
    @Test void trademarkCaseAndWhitespaceAreEquivalent(){assertEquals(SteamResolver.normalize(" Portal  2™ "),SteamResolver.normalize("portal 2"));assertNotEquals(SteamResolver.normalize("Game"),SteamResolver.normalize("Game: Remastered"));}
    @Test void duplicateExactResultsAreAmbiguous() throws Exception {
        assertEquals("AMBIGUOUS",SteamResolver.candidate(mapper.readTree("{\"items\":[{\"id\":1,\"name\":\"Game\",\"type\":\"app\"},{\"id\":2,\"name\":\"Game\",\"type\":\"app\"}]}"),"Game").status());
    }
    @Test void differentEditionAndNonAppAreNotSelected() throws Exception {
        assertNull(SteamResolver.candidate(mapper.readTree("{\"items\":[{\"id\":1,\"name\":\"Game Remastered\",\"type\":\"app\"},{\"id\":2,\"name\":\"Game\",\"type\":\"bundle\"}]}"),"Game").appId());
    }
    @Test void confirmsOnlySameGameNotDlc() throws Exception {
        var game=mapper.readTree("{\"1\":{\"success\":true,\"data\":{\"steam_appid\":1,\"type\":\"game\",\"name\":\"Game\"}}}");
        assertTrue(SteamResolver.confirms(game,1,"Game"));assertFalse(SteamResolver.confirms(game,1,"Game II"));assertFalse(SteamResolver.confirms(game,2,"Game"));
        ((com.fasterxml.jackson.databind.node.ObjectNode)game.path("1").path("data")).put("type","dlc");assertFalse(SteamResolver.confirms(game,1,"Game"));
    }
}
