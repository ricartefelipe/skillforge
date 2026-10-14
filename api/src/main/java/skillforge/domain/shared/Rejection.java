package skillforge.domain.shared;

public final class Rejection extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Failure failure;

    public Rejection(Failure failure) {
        super(failure.wire());
        this.failure = failure;
    }

    public Failure failure() {
        return failure;
    }

}
