package com.commerceflow.product;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps the product module's own exceptions to HTTP; scoped to this module's controllers. */
@RestControllerAdvice(basePackageClasses = ProductController.class)
class ProductExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    ProblemDetail handleNotFound(ProductNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Product not found");
        return problem;
    }

    @ExceptionHandler(DuplicateSkuException.class)
    ProblemDetail handleDuplicateSku(DuplicateSkuException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Duplicate SKU");
        return problem;
    }

    @ExceptionHandler(InvalidSortException.class)
    ProblemDetail handleInvalidSort(InvalidSortException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid sort");
        return problem;
    }

    @ExceptionHandler(ProductVersionConflictException.class)
    ProblemDetail handleProductVersionConflict(ProductVersionConflictException ex) {
        return getVersionConflictProblemDetail(ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleProductVersionConflictAtHibernate(OptimisticLockingFailureException ex) {
        return getVersionConflictProblemDetail("The product was changed by someone else; reload and retry");
    }

    private static ProblemDetail getVersionConflictProblemDetail(String ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex);
        problem.setTitle("Version conflict");
        return problem;
    }

}
