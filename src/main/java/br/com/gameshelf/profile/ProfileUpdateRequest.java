package br.com.gameshelf.profile;
import jakarta.validation.constraints.*;

public record ProfileUpdateRequest(@NotBlank @Size(min=2,max=80) String displayName,
    @Size(max=500) String bio,@Email @Size(max=254) String email,
    @Size(max=255) String avatarUrl,@Size(max=72) String currentPassword) {}
