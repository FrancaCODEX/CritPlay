package br.com.critplay.media;
import br.com.critplay.shared.ServiceUnavailableException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.io.IOException;

@Service
public class ImageCleanupService {
    private final ImageService images;private final JdbcTemplate db;
    public ImageCleanupService(ImageService images,JdbcTemplate db){this.images=images;this.db=db;}
    private List<Path> candidates(){
        var used=new HashSet<String>();
        db.query("SELECT cover_url FROM games WHERE cover_url IS NOT NULL",rs->{used.add(rs.getString(1));});
        db.query("SELECT avatar_url FROM app_users WHERE avatar_url IS NOT NULL",rs->{used.add(rs.getString(1));});
        Path directory=images.directory();if(!Files.isDirectory(directory))return List.of();
        var cutoff=Instant.now().minus(Duration.ofHours(24));
        try(var files=Files.list(directory)){
            return files.filter(p->p.getFileName().toString().matches("[0-9]+-[0-9a-f-]{36}\\.(png|jpg)"))
                .filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))
                .filter(p->!used.contains("/media/"+p.getFileName()))
                .filter(p->{try{return Files.getLastModifiedTime(p,LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff);}catch(IOException e){return false;}})
                .sorted().limit(1000).toList();
        }catch(IOException e){throw new ServiceUnavailableException("Não foi possível examinar as imagens.");}
    }
    @Transactional public Map<String,Object> preview(){
        images.lockReferences();var files=candidates();long bytes=0;
        for(var file:files)try{bytes+=Files.size(file);}catch(IOException ignored){}
        return Map.of("count",files.size(),"bytes",bytes,"graceHours",24,"limit",1000);
    }
    @Transactional public Map<String,Object> clean(){
        images.lockReferences();int removed=0,failed=0;
        // Recalcula após obter a trava. Uma prévia antiga nunca decide a exclusão.
        for(var file:candidates())try{if(Files.deleteIfExists(file))removed++;}catch(IOException e){failed++;}
        return Map.of("removed",removed,"failed",failed,"graceHours",24);
    }
}
