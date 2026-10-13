package br.com.critplay.social;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;
@RestController
@RequestMapping("/api/blocks")
public class BlockController {
    private final BlockService service;public BlockController(BlockService service){this.service=service;}
    @GetMapping public List<Map<String,Object>> list(Principal actor){return service.list(actor.getName());}
    @PutMapping("/{username}") public Map<String,String> block(Principal actor,@PathVariable String username){service.change(actor.getName(),username,true);return Map.of("message","Usuário bloqueado.");}
    @DeleteMapping("/{username}") public Map<String,String> unblock(Principal actor,@PathVariable String username){service.change(actor.getName(),username,false);return Map.of("message","Bloqueio removido. Conexões anteriores não são restauradas.");}
}
