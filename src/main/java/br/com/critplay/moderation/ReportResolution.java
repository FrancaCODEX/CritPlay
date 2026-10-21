package br.com.critplay.moderation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ReportResolution(@NotNull @Pattern(regexp = "RESOLVED|DISMISSED") String status,
                               Boolean hideReview) {}
