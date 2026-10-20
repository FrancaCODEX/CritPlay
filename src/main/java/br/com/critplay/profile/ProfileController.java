package br.com.critplay.profile;

import br.com.critplay.social.SocialService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final ProfileService service;
    private final SocialService social;

    public ProfileController(ProfileService service, SocialService social) {
        this.service = service;
        this.social = social;
    }

    @GetMapping
    public Map<String, Object> get(Principal principal) {
        return view(principal.getName());
    }

    @PutMapping
    public Map<String, Object> update(Principal principal, @Valid @RequestBody ProfileUpdateRequest request) {
        service.update(principal.getName(), request);
        return view(principal.getName());
    }

    @PutMapping("/gamer")
    public Map<String, Object> gamer(Principal principal, @Valid @RequestBody GamerProfileRequest body) {
        service.updateGamer(principal.getName(), body);
        return view(principal.getName());
    }

    @PutMapping("/password")
    public Map<String, String> password(Principal principal, @Valid @RequestBody PasswordChangeRequest body,
                                         HttpServletRequest request, HttpServletResponse response) {
        service.changePassword(principal.getName(), body);
        new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
        return Map.of("message", "Senha alterada. Entre novamente com sua nova senha.");
    }

    private Map<String, Object> view(String username) {
        var result = new LinkedHashMap<String, Object>(service.get(username));
        result.put("social", social.ownSocial(username));
        return result;
    }
}
