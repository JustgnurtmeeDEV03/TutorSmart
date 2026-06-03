// Bộ mặt ngoại giao

package com.dossier.api.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

@Builder
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL) // Không serialize field null
public class ErrorResponse {
    private final int status;
    private final String error;
    private final String message;
    private final List<String> details; // Validation errors

    @Builder.Default
    private final Instant timestamp = Instant.now();

    // Static Factory Method

    public static ErrorResponse of (HttpStatus status, String message) {
        return ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .build();
    }

    public static ErrorResponse of (HttpStatus status, String message,
                                    List<String> details) {
        return ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .details(details)
                .build();
    }
}
