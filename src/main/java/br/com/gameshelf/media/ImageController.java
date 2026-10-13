package br.com.gameshelf.media;
import br.com.gameshelf.users.UserService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.security.Principal;
import java.util.Map;

@RestController
public class ImageController {
    private final ImageService images; private final UserService users;
    public ImageController(ImageService images,UserService users) { this.images=images;this.users=users; }
    @PostMapping("/api/images") public Map<String,String> upload(@RequestParam MultipartFile file,Principal principal) {
        return Map.of("url",images.upload(users.require(principal.getName()).getId(),file));
    }
}
