package br.com.critplay.steam;

import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.*;
import java.util.*;

/** Best-effort adapter for the public store endpoints; no Steam/RAWG key required. */
@Component
public class SteamResolver {
    public record Result(String status, Long appId, String message) {}
    private record Cached(Result result, Instant until) {}
    private final Map<String,Cached> cache = new HashMap<>();
    private Instant retryAfter=Instant.EPOCH;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    public SteamResolver(ObjectMapper mapper) { this.mapper = mapper; }
    static String normalize(String title) {
        return Normalizer.normalize(title.replace("™", "").replace("®", ""),Normalizer.Form.NFKC)
            .strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
    static Result candidate(JsonNode response,String title) {
        var ids = new HashSet<Long>();
        for(var item:response.path("items")) if("app".equals(item.path("type").asText()) &&
            normalize(title).equals(normalize(item.path("name").asText())) && item.path("id").canConvertToLong() && item.path("id").asLong()>0)
            ids.add(item.path("id").asLong());
        if(ids.size()>1) return new Result("AMBIGUOUS",null,"Mais de um resultado com esse título. Revise o ID manualmente.");
        if(ids.isEmpty()) return new Result("NOT_FOUND",null,"Nenhuma correspondência exata na Steam. O ID permanece opcional.");
        return new Result("FOUND",ids.iterator().next(),"Steam App ID encontrado.");
    }
    static boolean confirms(JsonNode response,long id,String title) {
        var app=response.path(Long.toString(id));var data=app.path("data");
        return app.path("success").asBoolean() && "game".equals(data.path("type").asText()) &&
            data.path("steam_appid").asLong()==id && normalize(title).equals(normalize(data.path("name").asText()));
    }
    private JsonNode request(String path) throws Exception {
        var response=http.send(HttpRequest.newBuilder(URI.create("https://store.steampowered.com/api/"+path))
            .timeout(Duration.ofSeconds(5)).header("User-Agent","CritPlay/0.5.2").GET().build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200) throw new java.io.IOException("Store unavailable");
        return mapper.readTree(response.body());
    }
    public synchronized Result resolve(String title) {
        var normalized=normalize(title);var cached=cache.get(normalized);
        if(cached!=null && Instant.now().isBefore(cached.until()))return cached.result();
        if(Instant.now().isBefore(retryAfter))return new Result("UNAVAILABLE",null,"Steam indisponível. Tente novamente em um minuto.");
        Result result;
        try {
            var match=candidate(request("storesearch/?term="+URLEncoder.encode(title,StandardCharsets.UTF_8)+"&l=english&cc=br"),title);
            if(match.appId()!=null) {
                Thread.sleep(350);
                result=confirms(request("appdetails/?appids="+match.appId()+"&l=english&cc=br"),match.appId(),title)?match:
                    new Result("NOT_FOUND",null,"A Steam não confirmou o mesmo jogo; preencha manualmente se necessário.");
            } else result=match;
            Thread.sleep(350);
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();result=new Result("UNAVAILABLE",null,"Consulta Steam interrompida.");
        } catch(Exception e) {result=new Result("UNAVAILABLE",null,"Steam indisponível. Você pode tentar novamente ou preencher o ID manualmente.");}
        if(result.status().equals("UNAVAILABLE"))retryAfter=Instant.now().plus(Duration.ofMinutes(1));
        cache.entrySet().removeIf(e->Instant.now().isAfter(e.getValue().until()));if(cache.size()>=1000)cache.clear();
        cache.put(normalized,new Cached(result,Instant.now().plus(result.status().equals("UNAVAILABLE")?Duration.ofMinutes(1):Duration.ofHours(24))));
        return result;
    }
}
