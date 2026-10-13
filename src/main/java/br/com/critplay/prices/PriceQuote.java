package br.com.critplay.prices;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceQuote(String status, String message, String platform, String currency,
                         BigDecimal currentRetail, BigDecimal currentKeyshops,
                         BigDecimal historicalRetail, BigDecimal historicalKeyshops,
                         String sourceUrl, Instant fetchedAt) {
    public static PriceQuote unavailable(String status, String message, String url) {
        return new PriceQuote(status, message, "PC", null, null, null, null, null, url, null);
    }
}
