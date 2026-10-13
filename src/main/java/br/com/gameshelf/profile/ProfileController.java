package br.com.gameshelf.profile;
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
    public ProfileController(ProfileService service) { this.service=service; }
    @GetMapping public Map<String,Object> get(Principal principal) { return service.get(principal.getName()); }
    @PutMapping public Map<String,Object> update(Principal principal,@Valid @RequestBody ProfileUpdateRequest request) {
        service.update(principal.getName(),request);return service.get(principal.getName());
    }
    @PutMapping("/password") public Map<String,String> password(Principal principal,@Valid @RequestBody PasswordChangeRequest body,
        HttpServletRequest request,HttpServletResponse response) {
        service.changePassword(principal.getName(),body);
        new SecurityContextLogoutHandler().logout(request,response,SecurityContextHolder.getContext().getAuthentication());
        return Map.of("message","Senha alterada. Entre novamente com sua nova senha.");
    }
}
