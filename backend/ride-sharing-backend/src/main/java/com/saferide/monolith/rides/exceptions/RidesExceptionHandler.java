package com.saferide.monolith.rides.exceptions;

import com.saferide.monolith.kyc.exceptions.KycRequiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


/**
 * Also holds the application's single catch-all, hence the explicit ordering:
 * Spring stops at the first advice with an applicable handler, so an unordered
 * catch-all would shadow every other module's specific handlers.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class RidesExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RidesExceptionHandler.class);

    // Bean-validation is handled once, in UserExceptionHandler, which now
    // includes the same joined text under a "message" key.

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(RoleNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleRoleNotAllowed(RoleNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(ex.getMessage()));
    }

    /**
     * Handled here rather than in a new advice: every KYC-gated action lives
     * in the rides module, and adding an advice with its own catch-all would
     * make the resolution order between advices matter.
     */
    @ExceptionHandler(KycRequiredException.class)
    public ResponseEntity<ErrorResponse> handleKycRequired(KycRequiredException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        // Two drivers accepting the same ride at once: the second save loses
        // the @Version check. Surface it as a clean conflict, not a 500.
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("This ride was just taken by another driver."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("You do not have permission to perform this action."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("Unauthorized. Please log in again."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAny(Exception ex) {
        // The only place an unexpected failure is logged now that the
        // per-module catch-alls are gone — without this they'd vanish.
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Server error. Please try again later."));
    }
}