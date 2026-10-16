package br.com.critplay.social;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.*;

@RestController
public class SocialController {
    private final SocialService service;
    public SocialController(SocialService service){this.service=service;}
    @GetMapping("/api/people") public List<Map<String,Object>> search(Principal actor,@RequestParam(defaultValue="") String q){return service.search(actor.getName(),q);}
    @GetMapping("/api/people/{username}") public Map<String,Object> profile(Principal actor,@PathVariable String username){return service.profile(actor==null?null:actor.getName(),username);}
    @GetMapping("/api/people/{username}/followers") public Map<String,Object> followers(Principal actor,@PathVariable String username,@RequestParam(defaultValue="0") long after){return service.followPage(actor==null?null:actor.getName(),username,true,after);}
    @GetMapping("/api/people/{username}/following") public Map<String,Object> following(Principal actor,@PathVariable String username,@RequestParam(defaultValue="0") long after){return service.followPage(actor==null?null:actor.getName(),username,false,after);}
    @GetMapping("/api/social") public Map<String,Object> social(Principal actor){return service.connections(actor.getName());}
    public record PrivacyRequest(@NotNull ProfileVisibility visibility,@NotNull Boolean discoverable,Boolean libraryPublic,Boolean favoritesPublic,Boolean wishlistPublic,Boolean listsPublic){}
    @PutMapping("/api/profile/privacy") public Map<String,Object> privacy(Principal actor,@Valid @RequestBody PrivacyRequest body){return service.privacy(actor.getName(),body.visibility(),body.discoverable(),body.libraryPublic(),body.favoritesPublic(),body.wishlistPublic(),body.listsPublic());}
    @PutMapping("/api/people/{username}/follow") public Map<String,Object> follow(Principal actor,@PathVariable String username){return service.follow(actor.getName(),username,true);}
    @DeleteMapping("/api/people/{username}/follow") public Map<String,Object> unfollow(Principal actor,@PathVariable String username){return service.follow(actor.getName(),username,false);}
    public record FriendshipRequest(@NotNull @Pattern(regexp="REQUEST|ACCEPT|REJECT|CANCEL|REMOVE") String action){}
    @PutMapping("/api/people/{username}/friendship") public Map<String,Object> friendship(Principal actor,@PathVariable String username,@Valid @RequestBody FriendshipRequest body){return service.friendship(actor.getName(),username,body.action());}
}
