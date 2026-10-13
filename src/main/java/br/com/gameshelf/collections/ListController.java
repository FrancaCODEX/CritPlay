package br.com.gameshelf.collections;
import br.com.gameshelf.users.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;

@RestController @RequestMapping("/api/lists")
public class ListController {
    private final ListService service;private final UserService users;
    public ListController(ListService service,UserService users) {this.service=service;this.users=users;}
    private Long owner(Principal p) {return users.require(p.getName()).getId();}
    @GetMapping public List<Map<String,Object>> all(Principal p) {return service.all(owner(p));}
    @GetMapping("/{id}") public Map<String,Object> get(Principal p,@PathVariable Long id) {return service.get(owner(p),id);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> create(Principal p,@Valid @RequestBody ListRequest body) {return service.create(owner(p),body);}
    @PutMapping("/{id}") public Map<String,Object> update(Principal p,@PathVariable Long id,@Valid @RequestBody ListRequest body) {return service.update(owner(p),id,body);}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(Principal p,@PathVariable Long id) {service.delete(owner(p),id);}
    @PutMapping("/{id}/games/{gameId}") public Map<String,Object> add(Principal p,@PathVariable Long id,@PathVariable Long gameId) {return service.add(owner(p),id,gameId);}
    @DeleteMapping("/{id}/games/{gameId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void remove(Principal p,@PathVariable Long id,@PathVariable Long gameId) {service.remove(owner(p),id,gameId);}
}
