package com.vetclinic.shared.error;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(DomainException.class)
	ResponseEntity<ProblemDetail> domain(DomainException ex) {
		return problem(ex.getStatus(), ex.getCode(), ex.getMessage(), null);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex) {
		List<Map<String, String>> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
			.toList();
		return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", errors);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ProblemDetail> denied(AccessDeniedException ex) {
		return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied", null);
	}

	@ExceptionHandler({ MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
			HttpMessageNotReadableException.class })
	ResponseEntity<ProblemDetail> badRequest(Exception ex) {
		return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Malformed or missing request data", null);
	}

	// Framework exceptions that carry their own status (404, 405, 415...) keep it instead of becoming 500.
	@ExceptionHandler({ NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class,
			HttpMediaTypeNotSupportedException.class })
	ResponseEntity<ProblemDetail> frameworkStatus(Exception ex) {
		HttpStatus status = HttpStatus.valueOf(((ErrorResponse) ex).getStatusCode().value());
		return problem(status, status.name(), String.valueOf(status.getReasonPhrase()), null);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> fallback(Exception ex) {
		log.error("Unhandled exception", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error", null);
	}

	private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail,
			List<Map<String, String>> errors) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
		pd.setProperty("code", code);
		pd.setProperty("errors", errors == null ? List.of() : errors);
		return ResponseEntity.status(status).contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON).body(pd);
	}
}
