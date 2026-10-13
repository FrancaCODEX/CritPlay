package br.com.gameshelf.catalog;

import org.springframework.web.bind.annotation.*;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import java.net.URI;

@RestController
@RequestMapping("/api/games")
public class CatalogController {
    private final CatalogService service;
    public CatalogController(CatalogService service) { this.service = service; }
    @GetMapping
    public List<Game> list(@RequestParam(defaultValue = "") String q,
                          @RequestParam(defaultValue = "") String genre,
                          @RequestParam(defaultValue = "") String platform) {
        return service.search(q, genre, platform);
    }
    @GetMapping("/{id}") public Game get(@PathVariable Long id) { return service.find(id); }
    @PostMapping public ResponseEntity<Game> create(@Valid @RequestBody CreateGameRequest request) {
        var game = service.create(request);
        return ResponseEntity.created(URI.create("/api/games/" + game.getId())).body(game);
    }
}
