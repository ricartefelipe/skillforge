package skillforge.domain.shared;

public final class Marks {

    public static final int MIN = 0;

    public static final int MAX = 10;

    private Marks() {
    }

    public static int require(Integer value) {
        if (value == null || value < MIN || value > MAX) {
            throw new Rejection(Failure.INVALID_GRADE);
        }
        return value;
    }

}
