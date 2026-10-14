package skillforge.domain.shared;

public final class Texts {

    public static final int MAX = 4_000;

    private Texts() {
    }

    public static String require(String value, Failure blank) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new Rejection(blank);
        }
        if (text.length() > MAX) {
            throw new Rejection(Failure.TOO_LARGE);
        }
        return text;
    }

}
