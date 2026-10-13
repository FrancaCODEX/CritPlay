package br.com.gameshelf.users;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService users;
    public AuthController(UserService users) { this.users = users; }
    @GetMapping("/csrf") public ResponseEntity<?> csrf(CsrfToken csrf) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(Map.of("token", csrf.getToken(), "headerName", csrf.getHeaderName()));
    }
    @GetMapping("/me") public ResponseEntity<?> me(Principal principal) {
        var body = principal == null ? Map.of("authenticated", false)
            : Map.of("authenticated", true, "user", UserView.from(users.require(principal.getName())));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
    @PostMapping("/register") public ResponseEntity<UserView> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(users.register(request));
    }
}
