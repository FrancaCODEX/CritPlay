package br.com.gameshelf.catalog;

import jakarta.validation.constraints.*;
import java.util.Set;

public record CreateGameRequest(
    @NotBlank @Size(min = 2, max = 160) String title,
    @NotBlank @Size(min = 2, max = 40) String genre,
    @NotNull @Size(min = 1, max = 4) Set<@NotNull Platform> platforms,
    @Min(1970) @Max(2100) int releaseYear,
    @Positive Long steamAppId,
    @NotBlank @Size(min = 20, max = 2000) String description) {}
