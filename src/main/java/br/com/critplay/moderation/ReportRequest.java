package br.com.critplay.moderation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReportRequest(@NotBlank @Size(max = 30) String username,
                            @Positive Long gameId,
                            @NotBlank @Size(min = 10, max = 500) String reason) {}
