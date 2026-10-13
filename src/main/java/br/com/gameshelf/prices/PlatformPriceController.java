package br.com.gameshelf.prices;
import br.com.gameshelf.catalog.Platform;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

@RestController
public class PlatformPriceController {
    private final PlatformPriceService service;
    public PlatformPriceController(PlatformPriceService service){this.service=service;}
    @GetMapping("/api/games/{id}/prices/platforms") public List<Object> prices(@PathVariable Long id){return service.prices(id);}
    @GetMapping("/api/admin/games/{id}/price-sources") public Map<String,Object> sources(@PathVariable Long id){return service.sources(id);}
    public record SourcesRequest(@NotNull @Size(max=3) Map<@NotNull Platform,@NotNull @Positive Long> sources){}
    @PutMapping("/api/admin/games/{id}/price-sources") public Map<String,Object> update(@PathVariable Long id,@Valid @RequestBody SourcesRequest body){return service.update(id,body.sources());}
    @GetMapping("/api/admin/console-search") public List<Map<String,Object>> search(@RequestParam String q,@RequestParam Platform platform){return service.search(q,platform);}
}
