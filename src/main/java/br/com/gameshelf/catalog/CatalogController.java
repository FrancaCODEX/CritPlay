package br.com.gameshelf.catalog;

import org.springframework.web.bind.annotation.*;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import java.net.URI;
import java.security.Principal;
import br.com.gameshelf.users.UserService;
import br.com.gameshelf.prices.PlatformPriceService;
import jakarta.validation.constraints.*;

@RestController
@RequestMapping("/api/games")
public class CatalogController {
    private final CatalogService service;
    private final UserService users;private final PlatformPriceService prices;
    public CatalogController(CatalogService service,UserService users,PlatformPriceService prices) { this.service = service;this.users=users;this.prices=prices; }
    @GetMapping
    public List<Game> list(@RequestParam(defaultValue = "") String q,
                          @RequestParam(defaultValue = "") String genre,
                          @RequestParam(defaultValue = "") String platform) {
        return service.search(q, genre, platform);
    }
    @GetMapping("/{id}") public Game get(@PathVariable Long id) { return service.find(id); }
    @PostMapping public ResponseEntity<Game> create(@Valid @RequestBody CreateGameRequest request,Principal principal) {
        var game = service.create(request,users.require(principal.getName()).getId());
        return ResponseEntity.created(URI.create("/api/games/" + game.getId())).body(game);
    }
    @PutMapping("/{id}") public Game update(@PathVariable Long id,@Valid @RequestBody CreateGameRequest request,Principal principal) {
        var game=service.update(id,request,users.require(principal.getName()).getId());prices.invalidate(id);return game;
    }
    public record ImportRequest(@NotNull @Size(min=1,max=50) List<@Valid @NotNull CreateGameRequest> games) {}
    @PostMapping("/import") public ResponseEntity<List<Game>> importGames(@Valid @RequestBody ImportRequest request,Principal principal) {
        return ResponseEntity.status(201).body(service.importGames(request.games(),users.require(principal.getName()).getId()));
    }
}
