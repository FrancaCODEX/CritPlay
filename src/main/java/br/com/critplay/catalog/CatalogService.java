package br.com.critplay.catalog;

import br.com.critplay.shared.NotFoundException;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
import br.com.critplay.shared.BadRequestException;
import br.com.critplay.shared.ConflictException;
import br.com.critplay.media.ImageService;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    private final GameRepository repository;
    private final ImageService images;
    public CatalogService(GameRepository repository,ImageService images) { this.repository = repository;this.images=images; }
    public Game find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Jogo não encontrado."));
    }
    @Transactional public Game create(CreateGameRequest request,Long owner) {
        if(request.steamAppId()!=null&&repository.existsBySteamAppId(request.steamAppId())) throw new ConflictException("Este Steam App ID já está no catálogo.");
        var game=new Game(null,request.title(),request.genre(),"",request.releaseYear(),request.steamAppId(),request.description());
        apply(game,request,owner);return repository.save(game);
    }
    @Transactional public Game update(Long id,CreateGameRequest request,Long owner) {
        var game=find(id);
        if(request.steamAppId()!=null&&repository.existsBySteamAppIdAndIdNot(request.steamAppId(),id)) throw new ConflictException("Este Steam App ID já está no catálogo.");
        apply(game,request,owner);return repository.save(game);
    }
    @Transactional public List<Game> importGames(List<CreateGameRequest> requests,Long owner) {return requests.stream().map(r->create(r,owner)).toList();}
    private void apply(Game game,CreateGameRequest request,Long owner) {
        images.lockReferences();
        if (request.steamAppId() != null && !request.platforms().contains(Platform.PC))
            throw new BadRequestException("Um Steam App ID deve estar associado à plataforma PC.");
        var platforms = request.platforms().stream().sorted().map(Platform::getLabel).collect(Collectors.joining(", "));
        String cover=Objects.equals(game.getCoverUrl(),request.coverUrl())?game.getCoverUrl():images.ownedUrl(owner,request.coverUrl());
        StoreLinks links=request.storeLinks();
        if(links!=null) links=new StoreLinks(storeUrl(links.playstation(),Set.of("store.playstation.com")),
            storeUrl(links.xbox(),Set.of("www.xbox.com","xbox.com","www.microsoft.com","apps.microsoft.com")),
            storeUrl(links.nintendo(),Set.of("www.nintendo.com","www.nintendo.com.br","store.nintendo.com.br","ec.nintendo.com")));
        if(links!=null&&((links.playstation()!=null&&!request.platforms().contains(Platform.PLAYSTATION))
            ||(links.xbox()!=null&&!request.platforms().contains(Platform.XBOX))||(links.nintendo()!=null&&!request.platforms().contains(Platform.NINTENDO))))
            throw new BadRequestException("O link de loja deve corresponder a uma plataforma selecionada.");
        game.update(request.title().strip(),request.genre().strip(),platforms,request.releaseYear(),request.steamAppId(),request.description().strip(),cover,links);
    }
    private String storeUrl(String value,Set<String> hosts) {
        if(value==null||value.isBlank())return null;
        try {var uri=java.net.URI.create(value.strip());
            if(!"https".equals(uri.getScheme())||uri.getUserInfo()!=null||uri.getPort()!=-1||!hosts.contains(uri.getHost())) throw new IllegalArgumentException();
            return uri.toString();
        } catch(Exception ex) {throw new BadRequestException("Use um link HTTPS da loja oficial da plataforma.");}
    }
    public List<Game> search(String q, String genre, String platform) {
        // Catálogo inicial pequeno. Quando crescer, levar filtros e paginação ao banco.
        return repository.findAll().stream()
            .filter(g -> contains(g.getTitle(), q))
            .filter(g -> contains(g.getGenre(), genre))
            .filter(g -> contains(g.getPlatforms(), platform))
            .sorted(Comparator.comparing(Game::getId)).toList();
    }
    private boolean contains(String value, String query) {
        return query == null || query.isBlank() || value.toLowerCase(Locale.ROOT)
            .contains(query.strip().toLowerCase(Locale.ROOT));
    }
}
