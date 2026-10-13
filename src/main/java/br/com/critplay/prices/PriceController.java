package br.com.critplay.prices;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/games")
public class PriceController {
    private final PriceService service;
    public PriceController(PriceService service) { this.service = service; }
    @GetMapping("/{id}/prices") public PriceQuote get(@PathVariable Long id) { return service.get(id); }
}
