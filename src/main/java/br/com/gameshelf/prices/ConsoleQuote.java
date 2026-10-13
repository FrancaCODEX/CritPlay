package br.com.gameshelf.prices;
import java.math.BigDecimal;
import java.time.Instant;
public record ConsoleQuote(String status,String message,String platform,String currency,String region,BigDecimal currentRetail,
    BigDecimal originalPrice,Integer discountPercent,String edition,String storeUrl,String sourceUrl,Instant fetchedAt) {
    public static ConsoleQuote unavailable(String status,String message,String platform,String storeUrl) {
        return new ConsoleQuote(status,message,platform,null,null,null,null,null,null,storeUrl,"https://psprices.com/",null);
    }
}
