package br.com.critplay.steam;
import br.com.critplay.catalog.*;
import br.com.critplay.shared.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/steam")
public class SteamController {
    private final SteamResolver resolver;private final GameRepository games;private final SteamAssociationService association;
    public SteamController(SteamResolver resolver,GameRepository games,SteamAssociationService association){this.resolver=resolver;this.games=games;this.association=association;}
    public record Search(@NotBlank @Size(min=2,max=160) String title){}
    @PostMapping("/resolve") public SteamResolver.Result resolve(@Valid @RequestBody Search body){return resolver.resolve(body.title());}
    @PostMapping("/games/{id}") public SteamResolver.Result associate(@PathVariable long id){
        var game=games.findById(id).orElseThrow(()->new NotFoundException("Jogo não encontrado."));
        if(game.getSteamAppId()!=null)return new SteamResolver.Result("PRESERVED",game.getSteamAppId(),"ID existente preservado.");
        if(!game.getPlatforms().contains("PC"))throw new BadRequestException("Este jogo não está cadastrado para PC.");
        return association.apply(id,game.getTitle(),resolver.resolve(game.getTitle()));
    }
}
