package br.com.critplay.collections;
import jakarta.validation.constraints.*;
public record ListRequest(@NotBlank @Size(min=2,max=80) String name,@Size(max=500) String description) {}
