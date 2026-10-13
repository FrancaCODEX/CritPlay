package br.com.gameshelf.shared;
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
