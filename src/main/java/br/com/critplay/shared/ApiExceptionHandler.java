package br.com.critplay.shared;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<?> concurrent(Exception ex){return ResponseEntity.status(409).body(Map.of("message","O registro foi alterado ao mesmo tempo. Recarregue e tente novamente."));}
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<?> unavailable(ServiceUnavailableException ex) { return ResponseEntity.status(503).body(Map.of("message",ex.getMessage())); }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<?> uploadSize(Exception ex) { return ResponseEntity.status(413).body(Map.of("message","A imagem deve ter até 2 MB.")); }
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<?> conflict(ConflictException ex) {
        return ResponseEntity.status(409).body(Map.of("message", ex.getMessage()));
    }
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<?> badRequest(BadRequestException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<?> duplicateOrInvalidRelation(Exception ex) {
        return ResponseEntity.status(409).body(Map.of("message", "O registro já existe ou conflita com os dados atuais."));
    }
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<?> notFound(NotFoundException ex) {
        return ResponseEntity.status(404).body(Map.of("message", ex.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validation(MethodArgumentNotValidException ex) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage()).toList();
        return ResponseEntity.badRequest().body(Map.of("message", "Confira os dados enviados.", "errors", errors));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> invalidRequest(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("message", "JSON, identificador ou status inválido."));
    }
}
