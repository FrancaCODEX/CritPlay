package br.com.critplay.metadata;
import br.com.critplay.catalog.*;
import br.com.critplay.users.UserService;
import br.com.critplay.prices.PriceService;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import java.security.Principal;
@RestController
@RequestMapping("/api/admin/metadata")
public class MetadataController {
    private final RawgClient client;private final MetadataService service;private final UserService users;private final PriceService prices;
    public MetadataController(RawgClient client,MetadataService service,UserService users,PriceService prices){this.client=client;this.service=service;this.users=users;this.prices=prices;}
    @GetMapping("/status") public Map<String,Object> status(){return Map.of("configured",client.configured(),"provider","RAWG");}
    @GetMapping("/search") public List<MetadataGame> search(@RequestParam String q){return client.search(q);}
    @GetMapping("/{id}") public MetadataGame detail(@PathVariable long id){return client.detail(id);}
    public record ImportRequest(@Positive long rawgId,@NotNull @Valid CreateGameRequest game,@Positive Long targetGameId){}
    @PostMapping("/import") public Game importGame(Principal actor,@Valid @RequestBody ImportRequest body){var game=service.importGame(body.rawgId(),body.game(),body.targetGameId(),users.require(actor.getName()).getId());prices.invalidate(game.getId());return game;}
}
