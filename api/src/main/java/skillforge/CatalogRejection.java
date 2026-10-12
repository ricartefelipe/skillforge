package skillforge;

import org.springframework.http.HttpStatus;

class CatalogRejection extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    private final HttpStatus status;

    CatalogRejection(String code, HttpStatus status) {
        super(code);
        this.code = code;
        this.status = status;
    }

    String code() {
        return code;
    }

    HttpStatus status() {
        return status;
    }

}
