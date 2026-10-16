package br.com.critplay.media;

import br.com.critplay.shared.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.UUID;

@Service
public class ImageService {
    private final Path directory;
    private final br.com.critplay.shared.DatabaseLock lock;
    public ImageService(@Value("${app.media.directory}") String directory,br.com.critplay.shared.DatabaseLock lock) { this.lock=lock;this.directory=Path.of(directory).toAbsolutePath().normalize(); }
    public void lockReferences(){lock.acquire("media");}
    public Path directory() { return directory; }
    public String upload(Long userId, MultipartFile file) {
        if(file.isEmpty() || file.getSize()>2*1024*1024) throw new BadRequestException("Envie uma imagem PNG, JPEG ou GIF de até 2 MB.");
        try(var source=file.getInputStream();var input=ImageIO.createImageInputStream(source)) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw new BadRequestException("O arquivo não é uma imagem válida.");
            var reader=readers.next();
            try {
                reader.setInput(input,false,false);
                String format=reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                if(!format.equals("png")&&!format.equals("jpeg")&&!format.equals("gif")) throw new BadRequestException("Use PNG, JPEG ou GIF.");
                validateDimensions(reader.getWidth(0),reader.getHeight(0));
                Files.createDirectories(directory);
                String base=userId+"-"+UUID.randomUUID();
                String filename=base+(format.equals("jpeg")?".jpg":"."+format);
                var target=directory.resolve(filename);var poster=directory.resolve(base+".png");boolean complete=false;
                try {
                    // Re-encoding strips attached data; GIF retains only rendering/loop metadata.
                    if(format.equals("gif"))GifEncoder.write(reader,target,poster);
                    else if(!ImageIO.write(reader.read(0),format,target.toFile()))throw new BadRequestException("Não foi possível converter a imagem.");
                    if(Files.size(target)>2*1024*1024)throw new BadRequestException("A imagem processada excede 2 MB. Use um arquivo menor.");
                    complete=true;return "/media/"+filename;
                } finally {if(!complete){Files.deleteIfExists(target);if(format.equals("gif"))Files.deleteIfExists(poster);}}
            } finally { reader.dispose(); }
        } catch(BadRequestException ex) { throw ex; }
        catch(Exception ex) { throw new BadRequestException("Não foi possível salvar a imagem."); }
    }
    static void validateDimensions(long width,long height) {
        if(width<1||height<1||width>4096||height>4096||width*height>8_000_000)
            throw new BadRequestException("Imagem muito grande: limite de 4096 pixels e 8 megapixels.");
    }
    public String ownedUrl(Long userId,String value) {
        if(value==null||value.isBlank()) return null;
        String prefix="/media/"+userId+"-";
        if(!value.startsWith(prefix)||!value.matches("/media/[0-9]+-[0-9a-f-]{36}\\.(png|jpg|gif)")
            ||!Files.isRegularFile(directory.resolve(value.substring("/media/".length())),LinkOption.NOFOLLOW_LINKS))
            throw new BadRequestException("Escolha uma imagem enviada pela sua conta.");
        return value;
    }
}
