package br.com.critplay.media;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
@RestController
@RequestMapping("/api/admin/images/cleanup")
public class ImageCleanupController {
    private final ImageCleanupService service;
    public ImageCleanupController(ImageCleanupService service){this.service=service;}
    @GetMapping public Map<String,Object> preview(){return service.preview();}
    public record CleanupRequest(@NotNull @AssertTrue Boolean confirm){}
    @PostMapping public Map<String,Object> clean(@Valid @RequestBody CleanupRequest body){return service.clean();}
}
