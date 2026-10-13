package br.com.critplay.shared;
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
