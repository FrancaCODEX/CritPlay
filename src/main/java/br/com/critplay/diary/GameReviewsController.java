package br.com.critplay.diary;
import br.com.critplay.social.CommunityService;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
public class GameReviewsController {
    private final CommunityService service;
    public GameReviewsController(CommunityService service){this.service=service;}
    @GetMapping("/api/games/{gameId}/reviews")
    public CommunityService.Summary reviews(@PathVariable Long gameId,Principal principal){
        return service.reviews(gameId,principal==null?null:principal.getName());
    }
    @PutMapping("/api/reviews/{entryId}/like")
    public CommunityService.Like like(@PathVariable long entryId,Principal principal){return service.like(principal.getName(),entryId,true);}
    @DeleteMapping("/api/reviews/{entryId}/like")
    public CommunityService.Like unlike(@PathVariable long entryId,Principal principal){return service.like(principal.getName(),entryId,false);}
    @GetMapping("/api/feed")
    public CommunityService.FeedPage feed(Principal principal,@RequestParam(required=false) Long before){return service.feed(principal.getName(),before);}
}
