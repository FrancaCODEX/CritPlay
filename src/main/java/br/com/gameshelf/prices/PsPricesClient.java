package br.com.gameshelf.prices;

import br.com.gameshelf.catalog.Platform;
import br.com.gameshelf.shared.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

@Component
public class PsPricesClient {
    private final String key,region;private final ObjectMapper mapper;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public PsPricesClient(@Value("${psprices.api-key:}") String key,@Value("${psprices.region:br}") String region,ObjectMapper mapper){
        this.key=key;this.region=region.toLowerCase(Locale.ROOT);this.mapper=mapper;
        if(!this.region.matches("[a-z]{2}"))throw new IllegalArgumentException("Região PSprices inválida.");
    }
    public boolean configured(){return !key.isBlank();}
    private JsonNode request(String endpoint,String parameters) {
        if(!configured())throw new ServiceUnavailableException("Preços de consoles exigem uma chave PSprices B2B configurada pelo administrador.");
        try {
            var response=http.send(HttpRequest.newBuilder(URI.create("https://psprices.com/api/b2b/games/"+endpoint+"/?region="+region+"&"+parameters))
                .timeout(Duration.ofSeconds(10)).header("X-API-Key",key).header("Accept","application/json").GET().build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==429)throw new ServiceUnavailableException("Limite de consultas do PSprices atingido. Tente mais tarde.");
            if(response.statusCode()!=200)throw new ServiceUnavailableException("PSprices indisponível. Confira chave e acesso à região configurada.");
            return mapper.readTree(response.body());
        } catch(InterruptedException e){Thread.currentThread().interrupt();throw new ServiceUnavailableException("Consulta interrompida.");}
          catch(java.io.IOException e){throw new ServiceUnavailableException("Não foi possível consultar o PSprices agora.");}
    }
    public JsonNode search(String query,Platform platform) {
        if(platform==Platform.PC)throw new BadRequestException("Use a Steam para preços de PC.");
        if(query.strip().length()<2||query.length()>100)throw new BadRequestException("Busque por 2 a 100 caracteres.");
        return request("search","q="+URLEncoder.encode(query.strip(),StandardCharsets.UTF_8)+"&limit=10&platforms="+codes(platform));
    }
    public ConsoleQuote fetch(long id,Platform platform,String fallback) {
        if(!configured())return ConsoleQuote.unavailable("NOT_CONFIGURED","Preços de console ainda não configurados. Consulte a loja oficial.",platform.getLabel(),fallback);
        try {return parse(request("verify","ids="+id),id,platform,fallback);}
        catch(ServiceUnavailableException e){return ConsoleQuote.unavailable("UNAVAILABLE",e.getMessage(),platform.getLabel(),fallback);}
        catch(RuntimeException e){return ConsoleQuote.unavailable("UNAVAILABLE","Resposta de preços inválida.",platform.getLabel(),fallback);}
    }
    static String codes(Platform platform){return switch(platform){case PLAYSTATION->"PS5,PS4,PS3,PSVita,PSP";case XBOX->"XOne,X360";case NINTENDO->"Switch,Switch2,WiiU,3DS";case PC->"steam,epic";};}
    static boolean matches(JsonNode item,Platform platform) {
        var allowed=Set.of(codes(platform).split(","));
        for(var code:item.path("platforms"))if(allowed.contains(code.asText()))return true;
        return false;
    }
    static String safeUrl(JsonNode field,Set<String> hosts) {
        if(!field.isTextual())return null;
        try{var uri=URI.create(field.asText());return "https".equals(uri.getScheme())&&uri.getUserInfo()==null&&uri.getPort()==-1&&hosts.contains(uri.getHost())?uri.toString():null;}
        catch(IllegalArgumentException e){return null;}
    }
    static Set<String> storeHosts(Platform platform){return switch(platform){
        case PLAYSTATION->Set.of("store.playstation.com");case XBOX->Set.of("www.xbox.com","xbox.com","www.microsoft.com","apps.microsoft.com");
        case NINTENDO->Set.of("www.nintendo.com","www.nintendo.com.br","store.nintendo.com.br","ec.nintendo.com");case PC->Set.of("store.steampowered.com");};}
    private static BigDecimal money(JsonNode node){if(node.isNull()||node.isMissingNode())return null;var n=new BigDecimal(node.asText());if(n.signum()<0)throw new IllegalArgumentException();return n;}
    ConsoleQuote parse(JsonNode response,long id,Platform platform,String fallback) {
        for(var item:response.path("data")) {
            if(item.path("id").asLong()!=id)continue;
            if(!matches(item,platform))return ConsoleQuote.unavailable("NOT_FOUND","O produto associado não corresponde à plataforma. Peça ao administrador para revisar a edição.",platform.getLabel(),fallback);
            var pricing=item.path("pricing");var price=money(pricing.path("current_price"));
            String currency=pricing.path("currency").asText(),itemRegion=pricing.path("region").asText(item.path("region").asText());
            if(price==null||!currency.matches("[A-Z]{3}")||!region.equals(itemRegion))break;
            if(!item.path("is_directly_purchasable").asBoolean(false))return ConsoleQuote.unavailable("UNAVAILABLE","Esta edição exige condições de compra adicionais. Consulte a loja.",platform.getLabel(),fallback);
            String url=safeUrl(item.path("store_url"),storeHosts(platform)),source=safeUrl(item.path("psprices_url"),Set.of("psprices.com"));
            var discount=pricing.path("discount_percent");Integer percent=discount.isIntegralNumber()&&discount.asInt()>=0&&discount.asInt()<=100?discount.asInt():null;
            return new ConsoleQuote("LIVE","Preço da edição selecionada",platform.getLabel(),currency,region,price,money(pricing.path("original_price")),percent,
                item.path("variant_label").isTextual()?item.path("variant_label").asText():null,url==null?fallback:url,source==null?"https://psprices.com/":source,Instant.now());
        }
        return ConsoleQuote.unavailable("NOT_FOUND","Sem preço disponível para esta edição na região configurada.",platform.getLabel(),fallback);
    }
}
