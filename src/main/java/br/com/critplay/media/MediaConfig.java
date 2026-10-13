package br.com.critplay.media;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class MediaConfig implements WebMvcConfigurer {
    private final ImageService images;
    public MediaConfig(ImageService images) { this.images=images; }
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uri=images.directory().toUri().toString();
        registry.addResourceHandler("/media/**").addResourceLocations(uri.endsWith("/")?uri:uri+"/")
            .setCachePeriod(86400);
    }
}
