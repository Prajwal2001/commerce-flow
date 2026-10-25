package com.commerceflow.inventory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = ReservationController.class)
public class ReservationExceptionHandler {

    @ExceptionHandler(InsufficientStockException.class)
    ProblemDetail insufficientStock(InsufficientStockException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Insufficient stock");
        problemDetail.setTitle("Insufficient stock");
        return problemDetail;
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    ProblemDetail reservationNotFound(ReservationNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Reservation not found");
        problemDetail.setTitle("Reservation not found");
        return problemDetail;
    }
}
