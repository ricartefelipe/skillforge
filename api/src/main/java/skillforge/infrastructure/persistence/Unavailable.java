package skillforge.infrastructure.persistence;

public final class Unavailable extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public Unavailable(Throwable cause) {
        super(cause);
    }

}
