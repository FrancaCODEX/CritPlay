package br.com.gameshelf.catalog;

import br.com.gameshelf.shared.NotFoundException;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
import br.com.gameshelf.shared.BadRequestException;

@Service
public class CatalogService {
    private final GameRepository repository;
    public CatalogService(GameRepository repository) { this.repository = repository; }
    public Game find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Jogo não encontrado."));
    }
    public Game create(CreateGameRequest request) {
        if (request.steamAppId() != null && !request.platforms().contains(Platform.PC))
            throw new BadRequestException("Um Steam App ID deve estar associado à plataforma PC.");
        var platforms = request.platforms().stream().sorted().map(Platform::getLabel).collect(Collectors.joining(", "));
        return repository.save(new Game(null, request.title().strip(), request.genre().strip(), platforms,
            request.releaseYear(), request.steamAppId(), request.description().strip()));
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
