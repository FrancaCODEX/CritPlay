package br.com.gameshelf.prices;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class GgDealsClientTest {
    final ObjectMapper mapper = new ObjectMapper();
    final GgDealsClient client = new GgDealsClient("", "br", mapper);
    @Test void parsesPricesAndPreservesSourceLink() throws Exception {
        var root = mapper.readTree("""
            {"success":true,"data":{"420":{"url":"https://gg.deals/game/example/?ref=original",
            "prices":{"currentRetail":"0.00","currentKeyshops":"24.30",
            "historicalRetail":"2.89","historicalKeyshops":null,"currency":"BRL"}}}}
            """);
        var quote = client.parse(root, 420L);
        assertEquals("LIVE", quote.status());
        assertEquals(new BigDecimal("0.00"), quote.currentRetail());
        assertEquals(new BigDecimal("24.30"), quote.currentKeyshops());
        assertNull(quote.historicalKeyshops());
        assertEquals("BRL", quote.currency());
        assertEquals("https://gg.deals/game/example/?ref=original", quote.sourceUrl());
        assertNotNull(quote.fetchedAt());
    }
    @Test void handlesMissingGame() throws Exception {
        assertEquals("NOT_FOUND", client.parse(mapper.readTree("{\"success\":true,\"data\":{\"420\":null}}"),420L).status());
    }
    @Test void handlesUnsuccessfulResponse() throws Exception {
        assertEquals("UNAVAILABLE", client.parse(mapper.readTree("{\"success\":false}"),420L).status());
    }
    @Test void handlesIncompletePrices() throws Exception {
        assertEquals("UNAVAILABLE", client.parse(mapper.readTree("{\"success\":true,\"data\":{\"420\":{}}}"),420L).status());
    }
}
