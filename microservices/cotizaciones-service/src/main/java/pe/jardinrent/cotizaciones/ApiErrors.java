package pe.jardinrent.cotizaciones;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.support.WebExchangeBindException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> controlled(ResponseStatusException error) {
        var body = ProblemDetail.forStatusAndDetail(error.getStatusCode(), error.getReason() == null ? "Solicitud inválida" : error.getReason());
        return ResponseEntity.status(error.getStatusCode()).body(body);
    }
    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ProblemDetail> validation(WebExchangeBindException error) {
        var body = ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatus.BAD_REQUEST, "Revisa los campos obligatorios y sus límites");
        body.setProperty("campos", error.getFieldErrors().stream().map(field -> field.getField() + ": " + field.getDefaultMessage()).toList());
        return ResponseEntity.badRequest().body(body);
    }
}
