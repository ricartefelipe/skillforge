package skillforge.domain.shared;

public final class Names {

    public static final int MAX = 120;

    private Names() {
    }

    public static String require(String value, Failure blank) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty()) {
            throw new Rejection(blank);
        }
        if (name.length() > MAX) {
            throw new Rejection(Failure.TOO_LARGE);
        }
        return name;
    }

}
