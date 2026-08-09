package com.saferide.monolith.profile.exceptions;

import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
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

    /**
     * A unique column rejected the row — a plate or CNIC already on file.
     *
     * <p>This used to fall through to the global catch-all and surface as
     * "Server error. Please try again later.", which is wrong twice over: the
     * server is fine, and retrying can never help. The user has to change the
     * value, so the response has to say which one.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> duplicateValue(DataIntegrityViolationException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.CONFLICT.value(), duplicateMessage(ex));
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    /**
     * Names the offending field from Postgres' own detail line
     * ({@code Key (number)=(ABC-124) already exists}). Hibernate's constraint
     * names are generated hashes, so the column is the only readable handle.
     */
    private String duplicateMessage(DataIntegrityViolationException ex) {
        String detail = rootMessage(ex).toLowerCase();
        if (detail.contains("(number)")) {
            return "This car number is already registered to another driver.";
        }
        if (detail.contains("(cnic)")) {
            return "This CNIC is already registered to another account.";
        }
        if (detail.contains("(phone_no)") || detail.contains("(phoneno)")) {
            return "This phone number is already registered to another account.";
        }
        return "Some of these details are already registered to another account.";
    }

    private String rootMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? "" : cause.getMessage();
    }
}
