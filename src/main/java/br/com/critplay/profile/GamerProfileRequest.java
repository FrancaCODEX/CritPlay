package br.com.critplay.profile;
import jakarta.validation.constraints.*;
import java.util.List;
public record GamerProfileRequest(@Size(max=255) String bannerUrl,
    @NotNull @Size(max=4) List<@NotNull @Positive Long> featuredGameIds) {}
