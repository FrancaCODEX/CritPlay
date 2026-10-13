package br.com.gameshelf.diary;

import jakarta.validation.constraints.*;

public record DiaryRequest(@NotNull PlayStatus status,
                           @Min(1) @Max(5) Integer rating,
                           @Size(max = 2000) String review,
                           boolean favorite) {}
