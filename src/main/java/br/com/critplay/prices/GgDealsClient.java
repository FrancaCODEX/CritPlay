package br.com.critplay.prices;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;

@Component
public class GgDealsClient {
    private final String key;
    private final String region;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public GgDealsClient(@Value("${ggdeals.api-key}") String key,
                         @Value("${ggdeals.region}") String region, ObjectMapper mapper) {
        this.key = key; this.region = region; this.mapper = mapper;
    }
    public PriceQuote fetch(Long steamAppId) {
        String fallback = "https://gg.deals/steam/app/" + steamAppId + "/";
        if (key.isBlank()) return PriceQuote.unavailable("NOT_CONFIGURED",
            "Configure a chave do GG.deals para consultar preços reais de PC.", fallback);
        try {
            // A chave fica exclusivamente no servidor. Não registrar a URI nos logs.
            var uri = URI.create("https://api.gg.deals/v1/prices/by-steam-app-id/?ids=" + steamAppId
                + "&region=" + URLEncoder.encode(region, StandardCharsets.UTF_8)
                + "&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8));
            var response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json").header("User-Agent", "CritPlay/0.5 personal project")
                .GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) return PriceQuote.unavailable("RATE_LIMITED",
                "Limite do GG.deals atingido. Tente novamente mais tarde.", fallback);
            if (response.statusCode() != 200) return PriceQuote.unavailable("UNAVAILABLE",
                "O GG.deals está indisponível ou a chave não foi aceita.", fallback);
            return parse(mapper.readTree(response.body()), steamAppId);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return PriceQuote.unavailable("UNAVAILABLE", "Consulta interrompida.", fallback);
        } catch (Exception ex) {
            return PriceQuote.unavailable("UNAVAILABLE", "Não foi possível consultar preços agora.", fallback);
        }
    }
    // Separado do HTTP para testar o contrato sem chave e sem chamadas externas.
    PriceQuote parse(JsonNode root, Long id) {
        String fallback = "https://gg.deals/steam/app/" + id + "/";
        if (!root.path("success").asBoolean(false))
            return PriceQuote.unavailable("UNAVAILABLE", "O GG.deals não retornou preços.", fallback);
        var game = root.path("data").path(id.toString());
        if (game.isMissingNode() || game.isNull())
            return PriceQuote.unavailable("NOT_FOUND", "Jogo sem dados de preço no GG.deals.", fallback);
        var prices = game.path("prices");
        if (!prices.isObject() || !prices.path("currency").isTextual())
            return PriceQuote.unavailable("UNAVAILABLE", "Resposta de preços incompleta.", fallback);
        String url = game.path("url").asText(fallback);
        // Conservar integralmente o link retornado pelo provedor, inclusive parâmetros.
        if (!url.startsWith("https://gg.deals/")) url = fallback;
        return new PriceQuote("LIVE", "Menores preços de PC por categoria de loja.", "PC",
            prices.path("currency").asText(), decimal(prices, "currentRetail"),
            decimal(prices, "currentKeyshops"), decimal(prices, "historicalRetail"),
            decimal(prices, "historicalKeyshops"), url, Instant.now());
    }
    private BigDecimal decimal(JsonNode node, String field) {
        var value = node.path(field);
        return value.isNull() || value.isMissingNode() ? null : new BigDecimal(value.asText());
    }
}
