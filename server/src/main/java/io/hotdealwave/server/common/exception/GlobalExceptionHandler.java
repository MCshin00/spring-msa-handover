package io.hotdealwave.server.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/** Returns the shared not-found status and message for missing page resources. */
@ControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<String> notFound(NotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
    }
}
