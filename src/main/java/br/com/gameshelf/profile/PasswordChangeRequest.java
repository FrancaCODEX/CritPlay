package br.com.gameshelf.profile;
import jakarta.validation.constraints.*;
public record PasswordChangeRequest(@NotBlank @Size(max=72) String currentPassword,
    @NotBlank @Size(min=10,max=72) String newPassword) {}
