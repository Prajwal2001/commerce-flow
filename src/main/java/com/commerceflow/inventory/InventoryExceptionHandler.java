package com.commerceflow.inventory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = StockController.class)
public class InventoryExceptionHandler {

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
}
