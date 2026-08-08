package com.saferide.monolith.user.exceptions;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Explicitly ordered ahead of the other advices. Spring stops at the first
 * advice that can handle an exception, and advices without an {@code @Order}
 * all tie at lowest precedence — so without these numbers, which module's
 * handler runs is arbitrary, and a specific handler here would silently lose
 * to another module's catch-all.
 *
 * <p>Also owns the one app-wide bean-validation handler.
 */
@Order(1)
@RestControllerAdvice
public class UserExceptionHandler {

    /**
     * The single bean-validation handler for the whole app. Keeps the
     * per-field entries the auth screens read, and adds a joined {@code
     * message} so clients that only look for that key still show something
     * specific instead of a generic "bad request".
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> MethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        Map<String, String> errorResponse = new HashMap<>();
        BindingResult bindingResult = ex.getBindingResult();
        List<FieldError> errorList = bindingResult.getFieldErrors();
        for (FieldError fielderror : errorList) {
            errorResponse.put(fielderror.getField(), fielderror.getDefaultMessage());
        }
        String joined = errorList.stream()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        errorResponse.put("message", joined.isBlank() ? "Invalid request body" : joined);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> badCredentialsException(BadCredentialsException ex) {
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> illegalArgumentException(IllegalArgumentException ex) {
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> UserNotFoundException(UserNotFoundException ex) {
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(UserAlreadyExistException.class)
    public ResponseEntity<ErrorResponse> UserAlreadyExistsException(UserAlreadyExistException ex) {
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.CONFLICT.value(), ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> invalidTokenException(InvalidTokenException ex) {
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<ErrorResponse> tooManyAttemptsException(TooManyAttemptsException ex) {
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.TOO_MANY_REQUESTS.value(), ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(InvalidOnboardingTokenException.class)
    public ResponseEntity<ErrorResponse> invalidOnboardingTokenException(InvalidOnboardingTokenException ex) {
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    // No catch-all here on purpose. Spring consults @RestControllerAdvice
    // beans in order and stops at the first one with an applicable handler, so
    // a broad RuntimeException handler in this advice would swallow other
    // modules' specific exceptions (a ride conflict would 500 instead of 409).
    // RidesExceptionHandler carries the single, explicitly-last fallback.
}
