package com.saferide.user_service.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class LoginError extends RuntimeException {
    private final HttpStatusCode httpStatusCode;

    public LoginError(String message, HttpStatusCode httpStatusCode) {
        super(message);
        this.httpStatusCode = httpStatusCode;
    }

}
