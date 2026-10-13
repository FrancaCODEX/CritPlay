package br.com.critplay.metadata;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.critplay.catalog.Platform;
import br.com.critplay.shared.ServiceUnavailableException;
import static org.junit.jupiter.api.Assertions.*;
class RawgClientTest {
    final ObjectMapper mapper=new ObjectMapper();final RawgClient client=new RawgClient("",mapper);
    @Test void parsesDatesGenresPlainDescriptionAndFamilies() throws Exception {
        var game=client.parse(mapper.readTree("""
            {"id":99,"slug":"fixture-game","name":"Fixture game","released":"2021-03-12","description_raw":"Plain description",
            "genres":[{"name":"RPG"}],"parent_platforms":[{"platform":{"slug":"pc"}},{"platform":{"slug":"playstation"}}],
            "platforms":[{"platform":{"slug":"xbox-series-x"}},{"platform":{"slug":"nintendo-switch"}}],"background_image":"https://media.rawg.io/media/cover.jpg"}
            """));
        assertEquals(2021,game.releaseYear());assertEquals(4,game.platforms().size());assertTrue(game.platforms().contains(Platform.NINTENDO));assertEquals("Plain description",game.description());assertEquals("RPG",game.genre());
    }
    @Test void missingDatesUnsupportedPlatformsAndUntrustedImagesRemainAbsent() throws Exception {
        var game=client.parse(mapper.readTree("{\"id\":99,\"released\":null,\"platforms\":[{\"platform\":{\"slug\":\"ios\"}}],\"background_image\":\"https://attacker.example/cover.jpg\"}"));
        assertNull(game.releaseYear());assertTrue(game.platforms().isEmpty());assertNull(game.coverUrl());assertEquals("",game.description());
        assertNull(RawgClient.safeCover("https://media.rawg.io@attacker.example/img.png"));assertNull(RawgClient.safeCover("http://media.rawg.io/image"));
    }
    @Test void absentKeyNeverCallsProvider(){assertFalse(client.configured());assertThrows(ServiceUnavailableException.class,()->client.search("Hades"));assertThrows(ServiceUnavailableException.class,()->client.detail(99));}
}
