package br.com.gameshelf.prices;
import br.com.gameshelf.catalog.*;
import br.com.gameshelf.shared.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PlatformPriceService {
    private final CatalogService catalog;private final PriceService pc;private final PsPricesClient consoles;private final JdbcTemplate db;
    private record Cached(ConsoleQuote quote,Instant until){}
    private final ConcurrentHashMap<String,Cached> cache=new ConcurrentHashMap<>();
    public PlatformPriceService(CatalogService catalog,PriceService pc,PsPricesClient consoles,JdbcTemplate db){this.catalog=catalog;this.pc=pc;this.consoles=consoles;this.db=db;}
    private boolean supports(Game game,Platform platform){return Arrays.stream(game.getPlatforms().split(",")).map(String::strip).anyMatch(platform.getLabel()::equals);}
    public Map<String,Object> sources(Long gameId){
        catalog.find(gameId);var sources=new LinkedHashMap<String,Object>();
        db.query("SELECT platform,provider_id FROM game_console_sources WHERE game_id=?",rs->{sources.put(rs.getString(1),rs.getLong(2));},gameId);
        return Map.of("configured",consoles.configured(),"sources",sources);
    }
    public List<Object> prices(Long gameId){
        var game=catalog.find(gameId);var prices=new ArrayList<Object>();
        for(var platform:Platform.values()){
            if(!supports(game,platform))continue;
            if(platform==Platform.PC){prices.add(pc.get(gameId));continue;}
            String link=switch(platform){case PLAYSTATION->game.getStoreLinks().playstation();case XBOX->game.getStoreLinks().xbox();case NINTENDO->game.getStoreLinks().nintendo();default->null;};
            var ids=db.queryForList("SELECT provider_id FROM game_console_sources WHERE game_id=? AND platform=?",Long.class,gameId,platform.name());
            if(ids.isEmpty()){prices.add(ConsoleQuote.unavailable(consoles.configured()?"NOT_MAPPED":"NOT_CONFIGURED",consoles.configured()?"O administrador ainda não associou uma edição desta plataforma.":"Preços de consoles ainda não configurados. Consulte a loja oficial.",platform.getLabel(),link));continue;}
            String cacheKey=gameId+":"+platform+":"+ids.get(0);
            prices.add(cache.compute(cacheKey,(key,previous)->{
                if(previous!=null&&Instant.now().isBefore(previous.until()))return previous;
                var quote=consoles.fetch(ids.get(0),platform,link);return new Cached(quote,Instant.now().plus("LIVE".equals(quote.status())?Duration.ofHours(1):Duration.ofMinutes(1)));
            }).quote());
        }
        return prices;
    }
    public List<Map<String,Object>> search(String q,Platform platform){
        var results=new ArrayList<Map<String,Object>>();
        for(var item:consoles.search(q,platform).path("data")){
            if(!PsPricesClient.matches(item,platform)||item.path("id").asLong()<=0)continue;
            var row=new LinkedHashMap<String,Object>();row.put("id",item.path("id").asLong());row.put("name",item.path("name").asText());
            row.put("edition",item.path("variant_label").asText(""));row.put("platforms",item.path("platforms"));
            row.put("storeUrl",PsPricesClient.safeUrl(item.path("store_url"),PsPricesClient.storeHosts(platform)));results.add(row);
        }
        return results;
    }
    @Transactional public Map<String,Object> update(Long gameId,Map<Platform,Long> sources){
        var game=catalog.find(gameId);
        for(var row:sources.entrySet())if(row.getKey()==Platform.PC||row.getValue()==null||row.getValue()<=0||!supports(game,row.getKey()))throw new BadRequestException("Informe uma edição válida de uma plataforma do jogo.");
        db.update("DELETE FROM game_console_sources WHERE game_id=?",gameId);
        sources.forEach((platform,id)->db.update("INSERT INTO game_console_sources(game_id,platform,provider_id) VALUES (?,?,?)",gameId,platform.name(),id));
        invalidate(gameId);return sources(gameId);
    }
    public void invalidate(Long gameId){pc.invalidate(gameId);cache.keySet().removeIf(key->key.startsWith(gameId+":"));}
}
