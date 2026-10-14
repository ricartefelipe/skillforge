package skillforge.interfaces.http;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import skillforge.domain.shared.Rejection;
import skillforge.infrastructure.persistence.Unavailable;

@RestControllerAdvice
class HttpFailures {

    @ExceptionHandler(Rejection.class)
    ResponseEntity<ErrorBody> rejected(Rejection rejection) {
        HttpStatus status = switch (rejection.failure().kind()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case CONFLICT -> HttpStatus.CONFLICT;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
        };
        return ResponseEntity.status(status).body(new ErrorBody(rejection.failure().wire()));
    }

    @ExceptionHandler(Unavailable.class)
    ResponseEntity<Void> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

}
