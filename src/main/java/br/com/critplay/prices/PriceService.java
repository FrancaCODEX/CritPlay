package br.com.critplay.prices;

import br.com.critplay.catalog.CatalogService;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PriceService {
    private final CatalogService catalog;
    private final GgDealsClient client;
    private final ConcurrentHashMap<Long, Cached> cache = new ConcurrentHashMap<>();
    private record Cached(PriceQuote quote, Instant expiresAt) {}
    public PriceService(CatalogService catalog, GgDealsClient client) {
        this.catalog = catalog; this.client = client;
    }
    public PriceQuote get(Long gameId) {
        var game = catalog.find(gameId);
        if (game.getSteamAppId() == null) return PriceQuote.unavailable("UNSUPPORTED",
            "Este jogo não tem um Steam App ID para consultar preços de PC.", "https://gg.deals/");
        // compute serializa consultas simultâneas do mesmo jogo; jogos diferentes não se bloqueiam.
        return cache.compute(gameId, (id, previous) -> {
            if (previous != null && Instant.now().isBefore(previous.expiresAt())) return previous;
            var quote = client.fetch(game.getSteamAppId());
            var ttl = "LIVE".equals(quote.status()) ? Duration.ofHours(1) : Duration.ofMinutes(1);
            return new Cached(quote, Instant.now().plus(ttl));
        }).quote();
    }
    public void invalidate(Long gameId) {cache.remove(gameId);}
}
