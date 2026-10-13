package br.com.critplay.admin;
import br.com.critplay.users.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;
@RestController
@RequestMapping("/api/admin/accounts")
public class AccountAdminController {
    private final AccountAdminService service;
    public AccountAdminController(AccountAdminService service){this.service=service;}
    @GetMapping public List<Map<String,Object>> list(@RequestParam(defaultValue="") String q){return service.search(q);}
    public record AccessRequest(@NotNull Role role,@NotNull Boolean enabled){}
    @PutMapping("/{id}") public Map<String,Object> update(Principal actor,@PathVariable Long id,@Valid @RequestBody AccessRequest body){return service.update(actor.getName(),id,body.role(),body.enabled());}
}
