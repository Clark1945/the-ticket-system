package com.ticketsystem.frontend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.concurrent.TimeoutException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public String handleResponseStatusException(ResponseStatusException ex, Model model) {
        int status = ex.getStatusCode().value();
        log.warn("ResponseStatusException: {} - {}", status, ex.getReason());
        model.addAttribute("status",  status);
        model.addAttribute("message", ex.getReason());

        return switch (status) {
            case 403 -> "error/403";
            case 404 -> "error/404";
            case 503 -> "error/503";
            default  -> "error/503";
        };
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNoResourceFound(NoResourceFoundException ex, Model model) {
        log.warn("Resource not found: {}", ex.getMessage());
        model.addAttribute("status",  404);
        model.addAttribute("message", "The page you are looking for does not exist.");
        return "error/404";
    }

    @ExceptionHandler({WebClientRequestException.class, TimeoutException.class})
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String handleTimeout(Exception ex, Model model) {
        log.error("Downstream service unavailable or timed out: {}", ex.getMessage());
        model.addAttribute("status",  503);
        model.addAttribute("message", "The service is temporarily unavailable. Please try again later.");
        return "error/503";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGenericException(Exception ex, Model model) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        model.addAttribute("status",  500);
        model.addAttribute("message", "An unexpected error occurred. Please try again.");
        return "error/503";
    }
}
