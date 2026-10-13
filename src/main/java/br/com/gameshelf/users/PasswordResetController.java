package br.com.gameshelf.users;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/auth")
public class PasswordResetController {
    public record ForgotRequest(@NotBlank @Pattern(regexp="[a-zA-Z0-9_.-]{3,30}") String username,@NotBlank @Email String email) {}
    public record ResetRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token,@NotBlank @Size(min=10,max=72) String newPassword) {}
    private final PasswordResetService service;private final ResetMailer mail;
    public PasswordResetController(PasswordResetService service,ResetMailer mail) {this.service=service;this.mail=mail;}
    @GetMapping("/recovery-status") public Map<String,Boolean> status() {return Map.of("enabled",mail.enabled());}
    @PostMapping("/forgot-password") public Map<String,String> request(@Valid @RequestBody ForgotRequest body) {
        service.request(body.username(),body.email());return Map.of("message","Se os dados corresponderem a uma conta, enviaremos um link de recuperação.");
    }
    @PostMapping("/reset-password") public Map<String,String> reset(@Valid @RequestBody ResetRequest body) {
        service.reset(body.token(),body.newPassword());return Map.of("message","Senha redefinida. Entre com sua nova senha.");
    }
}
