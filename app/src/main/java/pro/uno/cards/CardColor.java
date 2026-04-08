package pro.uno.cards;

public final class CardColor {

    public static final int RED = 0;
    public static final int BLUE = 1;
    public static final int GREEN = 2;
    public static final int YELLOW = 3;

    public static final String[] STANDARD_COLORS = new String[]{"red", "blue", "green", "yellow"};

    private CardColor() {
    }

    public static boolean isStandardColor(String color) {
        if (color == null) {
            return false;
        }

        for (String item : STANDARD_COLORS) {
            if (item.equals(color)) {
                return true;
            }
        }
        return false;
    }

    public static String fromIndex(int color) {
        if (color < 0 || color >= STANDARD_COLORS.length) {
            return "";
        }
        return STANDARD_COLORS[color];
    }
}
