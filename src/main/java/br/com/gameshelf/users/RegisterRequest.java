package br.com.gameshelf.users;
import jakarta.validation.constraints.*;

public record RegisterRequest(
    @NotBlank @Pattern(regexp = "[a-zA-Z0-9_.-]{3,30}") String username,
    @NotBlank @Size(min = 2, max = 80) String displayName,
    @NotBlank @Size(min = 10, max = 72) String password) {}
