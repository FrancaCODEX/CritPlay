package br.com.critplay.metadata;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.*;
@RestController @RequestMapping("/api/admin/import-jobs")
public class BulkImportController {
    private final BulkImportService service;private final ImportJobStore store;
    public BulkImportController(BulkImportService service,ImportJobStore store){this.service=service;this.store=store;}
    public record StartRequest(@Min(500) @Max(2000) int count){}
    @GetMapping public List<ImportJob> list(){return store.list();}
    @PostMapping public ImportJob create(Principal actor,@Valid @RequestBody StartRequest body){return service.create(actor.getName(),body.count());}
    @PostMapping("/{id}/pause") public ImportJob pause(@PathVariable long id){store.pause(id,"Pausada pelo administrador.");return store.get(id);}
    @PostMapping("/{id}/resume") public ImportJob resume(Principal actor,@PathVariable long id){return service.resume(actor.getName(),id);}
}
