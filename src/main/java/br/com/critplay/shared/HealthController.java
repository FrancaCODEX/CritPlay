package br.com.critplay.shared;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;
@RestController
public class HealthController {
    private final JdbcTemplate db;public HealthController(JdbcTemplate db){this.db=db;}
    @GetMapping("/api/health") public ResponseEntity<Map<String,String>> health(){try{db.queryForObject("SELECT 1",Integer.class);return ResponseEntity.ok(Map.of("status","UP"));}catch(RuntimeException unavailable){return ResponseEntity.status(503).body(Map.of("status","DOWN"));}}
}
