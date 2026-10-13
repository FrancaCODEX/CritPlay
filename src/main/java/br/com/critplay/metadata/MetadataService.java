package br.com.critplay.metadata;
import br.com.critplay.catalog.*;
import br.com.critplay.shared.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class MetadataService {
    private final RawgClient client;private final CatalogService catalog;private final GameRepository games;
    public MetadataService(RawgClient client,CatalogService catalog,GameRepository games){this.client=client;this.catalog=catalog;this.games=games;}
    @Transactional public Game importGame(long rawgId,CreateGameRequest draft,Long targetId,Long owner){
        var metadata=client.detail(rawgId);
        var existing=games.findByRawgId(rawgId).orElse(null);
        if(existing!=null&&!existing.getId().equals(targetId))throw new ConflictException("Este jogo RAWG já está no catálogo. Abra-o para editar.");
        var game=targetId==null?catalog.create(draft,owner):catalog.update(targetId,draft,owner);
        if(game.getRawgId()!=null&&!game.getRawgId().equals(rawgId))throw new ConflictException("O jogo já está associado a outro registro RAWG.");
        game.importMetadata(rawgId,metadata.slug(),metadata.coverUrl());return games.save(game);
    }
}
