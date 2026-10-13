package br.com.critplay.diary;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.security.Principal;
import br.com.critplay.users.UserService;

@RestController
@RequestMapping("/api/diary")
public class DiaryController {
    private final DiaryService service;
    private final UserService users;
    public DiaryController(DiaryService service, UserService users) { this.service = service; this.users = users; }
    @GetMapping public List<DiaryEntry> list(Principal principal) {
        return service.list(users.require(principal.getName()).getId());
    }
    @PutMapping("/{gameId}") public DiaryEntry save(@PathVariable Long gameId,
        @Valid @RequestBody DiaryRequest request, Principal principal) {
        return service.save(users.require(principal.getName()).getId(), gameId, request);
    }
    @DeleteMapping("/{gameId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long gameId, Principal principal) {
        service.delete(users.require(principal.getName()).getId(), gameId);
    }
}
