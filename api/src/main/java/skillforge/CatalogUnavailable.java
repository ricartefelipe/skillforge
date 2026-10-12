package skillforge;

class CatalogUnavailable extends RuntimeException {

    private static final long serialVersionUID = 1L;

    CatalogUnavailable(Throwable cause) {
        super(cause);
    }

}
