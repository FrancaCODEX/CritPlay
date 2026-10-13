package br.com.gameshelf.social;
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
    @GetMapping("/api/social") public Map<String,Object> social(Principal actor){return service.connections(actor.getName());}
    public record PrivacyRequest(@NotNull ProfileVisibility visibility,@NotNull Boolean discoverable){}
    @PutMapping("/api/profile/privacy") public Map<String,Object> privacy(Principal actor,@Valid @RequestBody PrivacyRequest body){return service.privacy(actor.getName(),body.visibility(),body.discoverable());}
    @PutMapping("/api/people/{username}/follow") public Map<String,Object> follow(Principal actor,@PathVariable String username){return service.follow(actor.getName(),username,true);}
    @DeleteMapping("/api/people/{username}/follow") public Map<String,Object> unfollow(Principal actor,@PathVariable String username){return service.follow(actor.getName(),username,false);}
    public record FriendshipRequest(@NotNull @Pattern(regexp="REQUEST|ACCEPT|REJECT|CANCEL|REMOVE") String action){}
    @PutMapping("/api/people/{username}/friendship") public Map<String,Object> friendship(Principal actor,@PathVariable String username,@Valid @RequestBody FriendshipRequest body){return service.friendship(actor.getName(),username,body.action());}
}
