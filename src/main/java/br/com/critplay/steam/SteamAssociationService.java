package br.com.critplay.steam;
import br.com.critplay.catalog.*;
import br.com.critplay.prices.PriceService;
import br.com.critplay.shared.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class SteamAssociationService {
    private final DatabaseLock lock; private final GameRepository games; private final PriceService prices;
    public SteamAssociationService(GameRepository games,PriceService prices,DatabaseLock lock){this.lock=lock;this.games=games;this.prices=prices;}
    @Transactional public SteamResolver.Result apply(long id,String title,SteamResolver.Result result){
        lock.acquire("media");
        var game=games.findById(id).orElseThrow(()->new NotFoundException("Jogo não encontrado."));
        if(game.getSteamAppId()!=null)return new SteamResolver.Result("PRESERVED",game.getSteamAppId(),"ID existente preservado.");
        if(!game.getTitle().equals(title)||!game.getPlatforms().contains("PC"))throw new ConflictException("O jogo mudou durante a consulta. Tente novamente.");
        if(result.appId()==null)return result;
        if(games.existsBySteamAppId(result.appId()))return new SteamResolver.Result("CONFLICT",null,"Este Steam App ID pertence a outro jogo do catálogo.");
        game.assignSteamAppId(result.appId());games.saveAndFlush(game);prices.invalidate(id);return result;
    }
}
