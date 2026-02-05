package com.saferide.user_service.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestControllerAdvice
public class GlobalExceptionHandler {
    Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> MethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        Map<String, String> errorResponse = new HashMap<>();
        BindingResult bindingResult = ex.getBindingResult();
        List<FieldError> errorList = bindingResult.getFieldErrors();
        for (FieldError fielderror : errorList) {
            errorResponse.put(fielderror.getField(), fielderror.getDefaultMessage());
        }
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> UserNotFoundException(UserNotFoundException ex) {
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }


    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> runTimeException(RuntimeException ex) {
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), ex.getMessage());
        logger.error(ex.getMessage(), ex);
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(RegistrationError.class)
    public ResponseEntity<ErrorResponse> registrationError(RegistrationError ex) {
        ErrorResponse errorResponse = new ErrorResponse(ex.getStatus(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(errorResponse);
    }

    @ExceptionHandler(LoginError.class)
    public ResponseEntity<LoginErrorResponse> loginError(LoginError ex) {
        LoginErrorResponse errorResponse = new LoginErrorResponse(ex.getHttpStatusCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatusCode()).body(errorResponse);
    }
}
