package br.com.critplay.profile;
import jakarta.validation.Valid;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final ProfileService service;
    private final br.com.critplay.social.SocialService social;
    public ProfileController(ProfileService service,br.com.critplay.social.SocialService social) { this.service=service;this.social=social; }
    private Map<String,Object> view(String username) {var result=new java.util.LinkedHashMap<String,Object>(service.get(username));result.put("social",social.ownSocial(username));return result;}
    @GetMapping public Map<String,Object> get(Principal principal) { return view(principal.getName()); }
    @PutMapping public Map<String,Object> update(Principal principal,@Valid @RequestBody ProfileUpdateRequest request) {
        service.update(principal.getName(),request);return view(principal.getName());
    }
    @PutMapping("/gamer") public Map<String,Object> gamer(Principal principal,@Valid @RequestBody GamerProfileRequest body) {
        service.updateGamer(principal.getName(),body);return view(principal.getName());
    }
    @PutMapping("/password") public Map<String,String> password(Principal principal,@Valid @RequestBody PasswordChangeRequest body,
        HttpServletRequest request,HttpServletResponse response) {
        service.changePassword(principal.getName(),body);
        new SecurityContextLogoutHandler().logout(request,response,SecurityContextHolder.getContext().getAuthentication());
        return Map.of("message","Senha alterada. Entre novamente com sua nova senha.");
    }
}
