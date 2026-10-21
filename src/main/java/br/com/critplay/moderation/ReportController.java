package br.com.critplay.moderation;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
public class ReportController {
    private final ModerationService service;

    public ReportController(ModerationService service) {
        this.service = service;
    }

    @PostMapping("/api/reports")
    public Map<String, String> report(Principal actor, @Valid @RequestBody ReportRequest body) {
        service.report(actor.getName(), body);
        return Map.of("message", "Denúncia enviada para a moderação.");
    }

    @GetMapping("/api/admin/reports")
    public List<Map<String, Object>> list() {
        return service.list();
    }

    @PutMapping("/api/admin/reports/{id}")
    public Map<String, String> resolve(Principal actor, @PathVariable long id, @Valid @RequestBody ReportResolution body) {
        service.resolve(actor.getName(), id, body);
        return Map.of("message", "Denúncia analisada.");
    }
}
