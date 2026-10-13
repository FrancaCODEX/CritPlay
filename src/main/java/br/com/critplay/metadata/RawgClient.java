package br.com.critplay.metadata;
import br.com.critplay.catalog.Platform;
import br.com.critplay.shared.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RawgClient {
    private final String key;private final ObjectMapper mapper;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private record Cached(MetadataGame game,Instant until){}
    private final ConcurrentHashMap<Long,Cached> cache=new ConcurrentHashMap<>();
    public RawgClient(@Value("${rawg.api-key:}") String key,ObjectMapper mapper){this.key=key;this.mapper=mapper;}
    public boolean configured(){return !key.isBlank();}
    private JsonNode request(String path,String query){
        if(!configured())throw new ServiceUnavailableException("Configure RAWG_API_KEY para importar metadados. O plano pessoal da RAWG é gratuito.");
        try {
            // Host fixo. A chave nunca vai para o navegador nem para mensagens de erro.
            var url="https://api.rawg.io/api/games"+path+"?key="+URLEncoder.encode(key,StandardCharsets.UTF_8)+query;
            var response=http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12)).header("User-Agent","CritPlay/0.5").GET().build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==404)throw new NotFoundException("Jogo não encontrado na RAWG.");
            if(response.statusCode()==429)throw new ServiceUnavailableException("Limite de consultas RAWG atingido. Tente mais tarde.");
            if(response.statusCode()!=200)throw new ServiceUnavailableException("RAWG indisponível. Confira sua chave.");
            return mapper.readTree(response.body());
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new ServiceUnavailableException("Consulta interrompida.");}
        catch(java.io.IOException e){throw new ServiceUnavailableException("Não foi possível consultar a RAWG agora.");}
    }
    public List<MetadataGame> search(String query){
        if(query.strip().length()<2||query.length()>100)throw new BadRequestException("Busque por 2 a 100 caracteres.");
        var games=new ArrayList<MetadataGame>();
        for(var item:request("","&page_size=10&search="+URLEncoder.encode(query.strip(),StandardCharsets.UTF_8)).path("results")){
            var game=parse(item);if(game.id()>0)games.add(game);
        }
        return games;
    }
    public List<MetadataGame> page(int page){
        if(page<1)throw new BadRequestException("Página inválida.");
        var games=new ArrayList<MetadataGame>();
        for(var item:request("","&page_size=40&page="+page+"&ordering=-added&exclude_additions=true&parent_platforms=1,2,3,7").path("results")){var game=parse(item);if(game.id()>0)games.add(game);}
        return games;
    }
    public MetadataGame detail(long id){
        if(id<=0)throw new BadRequestException("ID RAWG inválido.");
        if(!configured())throw new ServiceUnavailableException("Configure RAWG_API_KEY para importar metadados.");
        cache.entrySet().removeIf(e->Instant.now().isAfter(e.getValue().until()));
        var existing=cache.get(id);if(existing!=null)return existing.game();
        var game=parse(request("/"+id,""));if(game.id()!=id)throw new ServiceUnavailableException("Resposta RAWG incompatível com o jogo selecionado.");
        if(cache.size()>=200)cache.clear();cache.put(id,new Cached(game,Instant.now().plus(Duration.ofMinutes(10))));return game;
    }
    static String text(JsonNode node,int max){String s=node.isTextual()?node.asText().strip():"";return s.substring(0,Math.min(max,s.length()));}
    public static String safeCover(String url){
        if(url==null||url.length()>1000)return null;
        try{var uri=URI.create(url);return "https".equals(uri.getScheme())&&"media.rawg.io".equals(uri.getHost())&&uri.getUserInfo()==null&&uri.getPort()==-1?url:null;}catch(IllegalArgumentException e){return null;}
    }
    MetadataGame parse(JsonNode item){
        var platforms=EnumSet.noneOf(Platform.class);
        for(var entry:item.path("parent_platforms")){
            switch(entry.path("platform").path("slug").asText()){
                case "pc"->platforms.add(Platform.PC);case "playstation"->platforms.add(Platform.PLAYSTATION);
                case "xbox"->platforms.add(Platform.XBOX);case "nintendo"->platforms.add(Platform.NINTENDO);default->{}
            }
        }
        for(var entry:item.path("platforms")){
            String slug=entry.path("platform").path("slug").asText();
            if(slug.equals("pc"))platforms.add(Platform.PC);else if(slug.startsWith("playstation")||slug.equals("ps-vita")||slug.equals("psp"))platforms.add(Platform.PLAYSTATION);
            else if(slug.startsWith("xbox"))platforms.add(Platform.XBOX);else if(slug.startsWith("nintendo")||Set.of("wii","wii-u","gamecube","game-boy","game-boy-advance","game-boy-color","nes","snes").contains(slug))platforms.add(Platform.NINTENDO);
        }
        Integer year=null;try{year=LocalDate.parse(item.path("released").asText()).getYear();if(year<1970||year>2100)year=null;}catch(java.time.format.DateTimeParseException ignored){}
        String genre=item.path("genres").isArray()&&item.path("genres").size()>0?text(item.path("genres").get(0).path("name"),40):"";
        String slug=text(item.path("slug"),200);if(!slug.matches("[a-z0-9-]{1,200}"))slug=null;
        return new MetadataGame(item.path("id").asLong(),slug,text(item.path("name"),160),genre,Set.copyOf(platforms),year,text(item.path("description_raw"),2000),safeCover(item.path("background_image").asText(null)));
    }
}
