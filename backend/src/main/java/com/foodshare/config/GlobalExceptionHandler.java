package com.foodshare.config;

import com.foodshare.security.TooManyAttemptsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;

// Every error the frontend receives has the same simple shape: {"message": "..."}
//
// It extends ResponseEntityExceptionHandler, which already knows all the standard Spring MVC errors
// (wrong HTTP method = 405, missing parameter = 400, unknown URL = 404, ...) and keeps their
// correct status codes. We only change the BODY of those errors into our {"message": "..."} shape.
// On top of that, handleUnexpected() catches everything else, so a bug never leaks a stack trace.
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static ResponseEntity<Object> message(HttpStatusCode status, String text) {
        return ResponseEntity.status(status).body(Map.of("message", text));
    }

    // Our own errors, e.g. throw new ResponseStatusException(HttpStatus.CONFLICT, "...")
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handleStatus(ResponseStatusException e) {
        String text = e.getReason() == null ? "Request failed" : e.getReason();
        return message(e.getStatusCode(), text);
    }

    // Login or registration blocked by a rate limiter: 429 + a standard Retry-After header (in seconds)
    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<Object> handleTooMany(TooManyAttemptsException e) {
        long seconds = e.getRetryAfterSeconds();
        String wait = seconds < 60 ? seconds + " seconds" : ((seconds + 59) / 60) + " minute(s)";
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                .body(Map.of("message", e.getMessage() + " Please try again in " + wait + "."));
    }

    // Validation errors (e.g. empty food name)
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var error = e.getBindingResult().getFieldErrors().get(0);
        String text = error.getDefaultMessage();
        if (text == null || text.equals("must not be blank") || text.equals("must not be null")) {
            text = error.getField() + " is required";
        }
        return message(HttpStatus.BAD_REQUEST, text);
    }

    // Broken JSON, wrong category name, wrong date format, ...
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return message(HttpStatus.BAD_REQUEST, "Invalid request data");
    }

    // All the other standard Spring MVC errors: same status code as Spring chose, our body shape.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        HttpStatus known = HttpStatus.resolve(status.value());
        String text = known == null ? "Request failed" : known.getReasonPhrase();   // e.g. "Not Found"
        return ResponseEntity.status(status).headers(headers).body(Map.of("message", text));
    }

    // Spring Security's "not allowed" error, if it ever reaches a controller: keep it a 403, not a 500
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException e) {
        return message(HttpStatus.FORBIDDEN, "Access denied");
    }

    // Anything we did not expect (a bug, the database is down, ...): log the details on the SERVER only
    // and send the user a safe, generic message.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return message(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side. Please try again later.");
    }
}
