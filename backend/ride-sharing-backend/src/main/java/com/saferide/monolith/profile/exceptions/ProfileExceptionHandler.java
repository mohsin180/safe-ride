package com.saferide.monolith.profile.exceptions;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@Order(2)
@RestControllerAdvice
public class ProfileExceptionHandler {

    @ExceptionHandler(ProfileAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> UserNotFoundException(ProfileAlreadyExistsException ex) {
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.CONFLICT.value(), ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ProfileNotFoundException.class)
    public ResponseEntity<ErrorResponse> ProfileNotFoundException(ProfileNotFoundException ex) {
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    // Bean-validation is handled once, in UserExceptionHandler — this copy
    // also returned the wrong status (403 for a malformed body).

    // No catch-all here on purpose — see the note in UserExceptionHandler.
    // The single fallback lives in RidesExceptionHandler, ordered last.

    @ExceptionHandler(RoleNotAllowedException.class)
    public ResponseEntity<ErrorResponse> roleNotAllowedException(RoleNotAllowedException ex) {
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.FORBIDDEN.value(), ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

}
