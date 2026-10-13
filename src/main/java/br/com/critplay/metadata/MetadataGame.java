package br.com.critplay.metadata;
import br.com.critplay.catalog.Platform;
import java.util.Set;
public record MetadataGame(long id,String slug,String title,String genre,Set<Platform> platforms,Integer releaseYear,String description,String coverUrl){}
