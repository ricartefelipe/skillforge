package skillforge;

import org.springframework.http.HttpStatus;

final class Names {

    static final int MAX = 120;

    private Names() {
    }

    static String required(String value, String blankCode) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty()) {
            throw new CatalogRejection(blankCode, HttpStatus.BAD_REQUEST);
        }
        if (name.length() > MAX) {
            throw new CatalogRejection("too-large", HttpStatus.BAD_REQUEST);
        }
        return name;
    }

}
