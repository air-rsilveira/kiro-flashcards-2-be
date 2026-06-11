package com.flashcards.unit;

import com.flashcards.dto.ErrorResponse;
import com.flashcards.dto.ValidationErrorResponse;
import com.flashcards.exception.GlobalExceptionHandler;
import com.flashcards.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GlobalExceptionHandler.
 * Validates: Requirements 14.1, 14.2, 14.3, 14.4
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("MethodArgumentNotValidException produces 400 with validation messages list")
    void handleMethodArgumentNotValid_returns400WithMessagesList() {
        // Arrange: create a MethodArgumentNotValidException with field errors
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", "must not be blank"));
        bindingResult.addError(new FieldError("request", "description", "size must be between 0 and 500"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        // Act
        ResponseEntity<ValidationErrorResponse> response = handler.handleMethodArgumentNotValid(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().error()).isEqualTo("Validation Error");
        assertThat(response.getBody().timestamp()).isNotNull();
        assertThat(response.getBody().messages()).isNotEmpty();
        assertThat(response.getBody().messages()).hasSize(2);
        assertThat(response.getBody().messages()).contains("name: must not be blank");
        assertThat(response.getBody().messages()).contains("description: size must be between 0 and 500");
    }

    @Test
    @DisplayName("ResourceNotFoundException produces 404 with resource details")
    void handleResourceNotFound_returns404WithResourceDetails() {
        // Arrange
        UUID id = UUID.randomUUID();
        ResourceNotFoundException ex = new ResourceNotFoundException("Deck", "id", id);

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleResourceNotFound(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().error()).isEqualTo("Not Found");
        assertThat(response.getBody().timestamp()).isNotNull();
        assertThat(response.getBody().message()).contains("Deck");
        assertThat(response.getBody().message()).contains("id");
        assertThat(response.getBody().message()).contains(id.toString());
    }

    @Test
    @DisplayName("Generic exception produces 500 without internal details")
    void handleGenericException_returns500WithoutInternalDetails() {
        // Arrange: create an exception with internal detail that should NOT be exposed
        Exception ex = new RuntimeException("SQL Error: connection refused at jdbc:postgresql://localhost:5432/db");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().error()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().timestamp()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred. Please try again later.");
        // Ensure no internal details leak
        assertThat(response.getBody().message()).doesNotContain("SQL");
        assertThat(response.getBody().message()).doesNotContain("jdbc");
        assertThat(response.getBody().message()).doesNotContain("connection refused");
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException produces 405 with allowed methods")
    void handleMethodNotSupported_returns405WithAllowedMethods() {
        // Arrange
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("DELETE", new java.util.ArrayList<>(List.of("GET", "POST", "PUT")));

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(405);
        assertThat(response.getBody().error()).isEqualTo("Method Not Allowed");
        assertThat(response.getBody().timestamp()).isNotNull();
        assertThat(response.getBody().message()).contains("DELETE");
        assertThat(response.getBody().message()).contains("GET");
        assertThat(response.getBody().message()).contains("POST");
        assertThat(response.getBody().message()).contains("PUT");
    }
}
