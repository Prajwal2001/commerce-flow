package com.commerceflow.inventory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps the inventory module's own exceptions to HTTP; covers every controller in this package. */
@RestControllerAdvice(basePackageClasses = StockController.class)
class InventoryExceptionHandler {

    @ExceptionHandler(StockNotFoundException.class)
    ProblemDetail stockNotFound(StockNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Stock not found");
        return problemDetail;
    }

    @ExceptionHandler(StockAlreadyExistsException.class)
    ProblemDetail stockAlreadyExist(StockAlreadyExistsException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setTitle("Stock already exists");
        return problemDetail;
    }

    // Fixed texts: the client needs the outcome, not internals or our exception messages.
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
