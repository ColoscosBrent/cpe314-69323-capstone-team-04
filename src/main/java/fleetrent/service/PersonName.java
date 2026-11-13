package fleetrent.service;

import java.util.regex.Pattern;

/**
 * A person's name is typed in three boxes (last name, first name, middle name) and saved as one
 * text: "Reyes, Ana Santos". It has no screen code, so it can be unit tested.
 */
public final class PersonName {
    private static final int MIN_LETTERS = 2;
    private static final Pattern WHOLE = Pattern.compile(
            "\\p{L}[\\p{L} .'\\-]*, \\p{L}[\\p{L}.'\\-]* \\p{L}[\\p{L} .'\\-]*");

    private PersonName() {
    }

    /**
     * Joins the three parts: last name, a comma, the first name and the middle name. Extra spaces
     * are
     * removed.
     */
    public static String compose(String last, String first, String middle) {
        return clean(last) + ", " + clean(first) + " " + clean(middle);
    }

    /**
     * Returns null when the saved text looks like "Last, First Middle", otherwise a message.
     */
    public static String check(String composed) {
        String t = composed == null ? "" : composed.trim();
        if (t.isEmpty()) {
            return "The name is required: last name, first name and middle name.";
        }
        if (!WHOLE.matcher(t).matches()) {
            return "Enter the name as: last name, first name and middle name.";
        }
        String last = t.substring(0, t.indexOf(','));
        String first = t.substring(t.indexOf(',') + 2, t.indexOf(' ', t.indexOf(',') + 2));
        if (letters(last) < MIN_LETTERS || letters(first) < MIN_LETTERS) {
            return "The last name and the first name need at least 2 letters.";
        }
        return null;
    }

    private static String clean(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ");
    }

    private static int letters(String text) {
        return (int) text.chars().filter(Character::isLetter).count();
    }
}
