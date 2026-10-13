package br.com.gameshelf.prices;
import br.com.gameshelf.catalog.Platform;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class PsPricesClientTest {
    final ObjectMapper mapper=new ObjectMapper();
    final PsPricesClient client=new PsPricesClient("","br",mapper);
    String fixture(String platforms,String price,String url) {return """
        {"data":[{"id":42,"platforms":["%s"],"is_directly_purchasable":true,"variant_label":"Standard",
        "pricing":{"region":"br","currency":"BRL","current_price":%s,"original_price":199.90,"discount_percent":50},
        "store_url":"%s","psprices_url":"https://psprices.com/region-br/game/42/example"}]}
        """.formatted(platforms,price,url);}
    @Test void preservesCurrencyZeroDiscountAndExactEdition() throws Exception {
        var quote=client.parse(mapper.readTree(fixture("PS5","0","https://store.playstation.com/product/example")),42,Platform.PLAYSTATION,null);
        assertEquals("LIVE",quote.status());assertEquals(BigDecimal.ZERO,quote.currentRetail());assertEquals("BRL",quote.currency());assertEquals("Standard",quote.edition());assertEquals(50,quote.discountPercent());
    }
    @Test void wrongPlatformWrongRegionAndMissingIdsDoNotProducePrice() throws Exception {
        assertEquals("NOT_FOUND",client.parse(mapper.readTree(fixture("Switch","19.90","https://www.nintendo.com/game")),42,Platform.XBOX,null).status());
        assertEquals("NOT_FOUND",client.parse(mapper.readTree(fixture("XOne","19.90","https://www.xbox.com/game").replace("br","us")),42,Platform.XBOX,null).status());
        assertEquals("NOT_FOUND",client.parse(mapper.readTree("{\"data\":[]}"),42,Platform.XBOX,null).status());
    }
    @Test void refusesUntrustedLinksAndEntitlementGatedEditions() throws Exception {
        String data=fixture("XOne","19.90","https://attacker.example/");
        assertNull(client.parse(mapper.readTree(data),42,Platform.XBOX,null).storeUrl());
        assertEquals("UNAVAILABLE",client.parse(mapper.readTree(data.replace("true","false")),42,Platform.XBOX,null).status());
        assertEquals("NOT_CONFIGURED",client.fetch(42,Platform.XBOX,null).status());
    }
}
