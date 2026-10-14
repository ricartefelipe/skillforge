package skillforge;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiFailures {

    @ExceptionHandler(CatalogRejection.class)
    ResponseEntity<CatalogController.ErrorBody> rejected(CatalogRejection rejection) {
        return ResponseEntity.status(rejection.status()).body(new CatalogController.ErrorBody(rejection.code()));
    }

    @ExceptionHandler(CatalogUnavailable.class)
    ResponseEntity<Void> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

}
