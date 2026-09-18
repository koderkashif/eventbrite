package com.eventbrite.event.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/** One place that turns exceptions into consistent HTTP error responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.badRequest()
                .body(ApiError.withFieldErrors(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                        "Request body is invalid", fieldErrors));
    }

    // missing or malformed JSON body -> 400, not a 500
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                        "Request body is missing or not valid JSON"));
    }

    // e.g. ?category=NOT_A_CATEGORY - enum/param conversion failure
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                        "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getParameter().getParameterName() + "'"));
    }

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ApiError> handleEventNotFound(EventNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler({InvalidEventStateException.class, CapacityBelowBookingsException.class})
    public ResponseEntity<ApiError> handleEventState(RuntimeException ex) {
        String code = ex instanceof CapacityBelowBookingsException
                ? "CAPACITY_BELOW_BOOKINGS" : "INVALID_EVENT_STATE";
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT, code, ex.getMessage()));
    }

    @ExceptionHandler(InsufficientSeatsException.class)
    public ResponseEntity<ApiError> handleInsufficientSeats(InsufficientSeatsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT, "INSUFFICIENT_SEATS", ex.getMessage()));
    }

    // Losing side of an optimistic-lock race -> tell the client to retry
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Concurrent modification: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                        "This event was modified by another request. Please retry."));
    }

    // @PreAuthorize denials (e.g. a USER calling an admin endpoint) surface HERE, not in the
    // security filter chain - without this handler they fall into the catch-all 500.
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(HttpStatus.FORBIDDEN, "FORBIDDEN",
                        "You do not have permission to perform this action."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                        "Something went wrong. Please try again later."));
    }
}
