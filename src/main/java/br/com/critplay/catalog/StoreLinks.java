package br.com.critplay.catalog;
import jakarta.validation.constraints.Size;
public record StoreLinks(@Size(max=1000) String playstation,@Size(max=1000) String xbox,@Size(max=1000) String nintendo) {}
