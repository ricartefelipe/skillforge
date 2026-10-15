package skillforge.interfaces.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Rejection;

class HttpFailuresTest {

    private final HttpFailures failures = new HttpFailures();

    @Test
    void eachFailureKindBecomesItsHttpStatus() {
        assertEquals(HttpStatus.BAD_REQUEST, status(Failure.BLANK_TITLE));
        assertEquals(HttpStatus.CONFLICT, status(Failure.ALREADY_TAKEN));
        assertEquals(HttpStatus.NOT_FOUND, status(Failure.NOT_FOUND));
        assertEquals(HttpStatus.FORBIDDEN, status(Failure.FORBIDDEN));
    }

    @Test
    void aMissingDatabaseRefusesTraffic() {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failures.unavailable().getStatusCode());
    }

    private org.springframework.http.HttpStatusCode status(Failure failure) {
        return failures.rejected(new Rejection(failure)).getStatusCode();
    }

}
