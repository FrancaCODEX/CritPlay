package br.com.gameshelf.media;

import br.com.gameshelf.shared.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.UUID;

@Service
public class ImageService {
    private final Path directory;
    public ImageService(@Value("${app.media.directory}") String directory) { this.directory=Path.of(directory).toAbsolutePath().normalize(); }
    public Path directory() { return directory; }
    public String upload(Long userId, MultipartFile file) {
        if(file.isEmpty() || file.getSize()>2*1024*1024) throw new BadRequestException("Envie uma imagem PNG ou JPEG de até 2 MB.");
        try(var source=file.getInputStream();var input=ImageIO.createImageInputStream(source)) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw new BadRequestException("O arquivo não é uma imagem válida.");
            var reader=readers.next();
            try {
                reader.setInput(input,true,true);
                String format=reader.getFormatName().toLowerCase();
                if(!format.equals("png")&&!format.equals("jpeg")) throw new BadRequestException("Use PNG ou JPEG.");
                long width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1||height<1||width>4096||height>4096||width*height>8_000_000)
                    throw new BadRequestException("Imagem muito grande: limite de 4096 pixels e 8 megapixels.");
                var image=reader.read(0);
                Files.createDirectories(directory);
                String filename=userId+"-"+UUID.randomUUID()+ (format.equals("png")?".png":".jpg");
                var target=directory.resolve(filename);
                // Reencodificar elimina metadados e conteúdos anexados ao arquivo original.
                if(!ImageIO.write(image,format,target.toFile())) throw new BadRequestException("Não foi possível converter a imagem.");
                return "/media/"+filename;
            } finally { reader.dispose(); }
        } catch(BadRequestException ex) { throw ex; }
        catch(Exception ex) { throw new BadRequestException("Não foi possível salvar a imagem."); }
    }
    public String ownedUrl(Long userId,String value) {
        if(value==null||value.isBlank()) return null;
        String prefix="/media/"+userId+"-";
        if(!value.startsWith(prefix)||!value.matches("/media/[0-9]+-[0-9a-f-]{36}\\.(png|jpg)")
            ||!Files.isRegularFile(directory.resolve(value.substring("/media/".length()))))
            throw new BadRequestException("Escolha uma imagem enviada pela sua conta.");
        return value;
    }
}
