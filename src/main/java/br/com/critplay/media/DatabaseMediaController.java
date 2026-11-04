package br.com.critplay.media;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import br.com.critplay.shared.NotFoundException;

@RestController
@ConditionalOnProperty(name="app.media.storage",havingValue="database")
public class DatabaseMediaController {
    private final DatabaseMediaStore store;
    public DatabaseMediaController(DatabaseMediaStore store){this.store=store;}
    @GetMapping("/media/{name}") public ResponseEntity<byte[]> get(@PathVariable String name) {
        if(!name.matches("[0-9]+-[0-9a-f-]{36}\\.(png|jpg|gif)"))throw new NotFoundException("Imagem não encontrada.");
        var bytes=store.read(name).orElseThrow(()->new NotFoundException("Imagem não encontrada."));
        String type=name.endsWith(".gif")?"image/gif":name.endsWith(".jpg")?"image/jpeg":"image/png";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type))
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(1))).body(bytes);
    }
}
