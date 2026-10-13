package br.com.critplay.prices;

import br.com.critplay.catalog.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PriceServiceTest {
    @Test void repeatedReadsUseCacheInsteadOfCallingProviderAgain() {
        var catalog = mock(CatalogService.class);
        var client = mock(GgDealsClient.class);
        when(catalog.find(1L)).thenReturn(new Game(1L,"Test","RPG","PC",2022,420L,"Test"));
        var quote = PriceQuote.unavailable("NOT_CONFIGURED", "Configure a chave", "https://gg.deals/");
        when(client.fetch(420L)).thenReturn(quote);
        var service = new PriceService(catalog, client);
        assertSame(quote, service.get(1L)); assertSame(quote, service.get(1L));
        verify(client, times(1)).fetch(420L);
    }
    @Test void consoleOnlyGameDoesNotCallSteamPriceProvider() {
        var catalog = mock(CatalogService.class);
        var client = mock(GgDealsClient.class);
        when(catalog.find(1L)).thenReturn(new Game(1L,"Test","RPG","Nintendo",2022,null,"Test"));
        assertEquals("UNSUPPORTED", new PriceService(catalog, client).get(1L).status());
        verifyNoInteractions(client);
    }
}
