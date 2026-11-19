package io.github.viturin.spellcards.adapters.in.rest;

import io.github.viturin.spellcards.application.port.out.SpellCardJobPublicationException;
import io.github.viturin.spellcards.application.service.SpellCardJobResults.ResultException;
import io.github.viturin.spellcards.generated.model.ErrorResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class RestExceptionHandler {
    @ExceptionHandler(ResultException.class)
    public ResponseEntity<ErrorResponse> handleJobResultFailure(ResultException exception) {
        var status = switch (exception.problem()) {
            case JOB_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case JOB_NOT_DOWNLOADABLE -> HttpStatus.CONFLICT;
            case JOB_EXPIRED -> HttpStatus.GONE;
            case ARTIFACT_UNAVAILABLE -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        var error = new ErrorResponse(exception.problem().name(), exception.getMessage());
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON).body(error);
    }

    @ExceptionHandler(SpellCardJobPublicationException.class)
    public ResponseEntity<ErrorResponse> handleJobPublicationFailure(SpellCardJobPublicationException exception) {
        ErrorResponse response = new ErrorResponse("JOB_QUEUE_UNAVAILABLE", exception.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ErrorResponse response = new ErrorResponse("INVALID_ARGUMENT", ex.getMessage());
        response.setDetails(List.of(ex.getMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
