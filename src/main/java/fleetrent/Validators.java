package fleetrent;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Rules for what may be typed into each kind of text field, and whether the finished text is valid.
 * It has no screen code, so it can be unit tested. The screen uses typingAllowed() to block bad
 * keys while the user types, and check() to show an error message under the field.
 */
public final class Validators {
    private Validators() {
    }

    /** The kinds of values the validators check. */
    public enum Kind {
        NAME("Letters, spaces and . ' - only.",
                Pattern.compile("[\\p{L} .'\\-]{0,60}")),
        CONTACT("Digits, spaces and - only (a + is allowed at the start).",
                Pattern.compile("\\+?[0-9 \\-]{0,20}")),
        EMAIL("Letters, digits and @ . _ + - only (no spaces).",
                Pattern.compile("[A-Za-z0-9@._+\\-]{0,80}")),
        ADDRESS("Letters, digits, spaces and , . # / ' - only.",
                Pattern.compile("[\\p{L}0-9 ,.#/'\\-]{0,120}")),
        PLATE("Letters, digits, spaces and - only (up to 10 characters).",
                Pattern.compile("[A-Za-z0-9 \\-]{0,10}")),
        DIGITS("Digits 0 to 9 only.",
                Pattern.compile("[0-9]{0,7}")),
        MONEY("Digits and one decimal point only (up to 2 decimals).",
                Pattern.compile("[0-9]{0,6}(\\.[0-9]{0,2})?")),
        VEHICLE_TEXT("Letters, digits, spaces and . & ' ( ) / + - only.",
                Pattern.compile("[\\p{L}0-9 .&'()/+\\-]{0,50}"));

        private final String rejectMessage;
        private final Pattern allowed;

        Kind(String rejectMessage, Pattern allowed) {
            this.rejectMessage = rejectMessage;
            this.allowed = allowed;
        }

        /**
         * The message shown when the user types a key that is not allowed.
         */
        public String rejectMessage() {
            return rejectMessage;
        }

        /**
         * True if this text (the whole field content after the key press) may exist in the field.
         */
        public boolean typingAllowed(String text) {
            if (text == null) {
                return false;
            }
            if (!allowed.matcher(text).matches()) {
                return false;
            }
            if (this == EMAIL) {
                long ats = text.chars().filter(c -> c == '@').count();
                return ats <= 1;
            }
            return true;
        }
    }

    private static final Pattern NAME_OK = Pattern.compile("\\p{L}[\\p{L} .'\\-]*");
    private static final Pattern CONTACT_OK = Pattern.compile("\\+?[0-9][0-9 \\-]*");
    private static final Pattern EMAIL_OK =
            Pattern.compile("[A-Za-z0-9._+\\-]+@[A-Za-z0-9\\-]+(\\.[A-Za-z0-9\\-]+)+");

    /**
     * Returns null when the text is fine, otherwise a short message for the user. Empty text is an
     * error only when required is true.
     */
    public static String check(Kind kind, String text, boolean required) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            return required ? "This field is required." : null;
        }
        switch (kind) {
            case NAME:
                if (!NAME_OK.matcher(t).matches() || letterCount(t) < 2) {
                    return "Enter a real name: at least 2 letters, starting with a letter.";
                }
                return null;
            case CONTACT:
                int digits = t.replaceAll("[^0-9]", "").length();
                if (!CONTACT_OK.matcher(t).matches() || digits < 7 || digits > 15) {
                    return "A phone number needs 7 to 15 digits.";
                }
                return null;
            case EMAIL:
                return EMAIL_OK.matcher(t).matches() ? null : "Enter an email like name@example.com.";
            case ADDRESS:
                if (t.length() < 5 || letterCount(t) < 1) {
                    return "The address is too short (at least 5 characters with a letter).";
                }
                return null;
            case PLATE:
                int alnum = t.replaceAll("[^A-Za-z0-9]", "").length();
                if (alnum < 3) {
                    return "A plate number needs at least 3 letters or digits.";
                }
                return null;
            case DIGITS:
                if (!t.matches("[0-9]{1,7}")) {
                    return "Enter a whole number (digits only).";
                }
                return null;
            case MONEY:
                try {
                    double value = Double.parseDouble(t);
                    if (!(value > 0)) {
                        return "Enter an amount above 0, for example 1500 or 1500.50.";
                    }
                } catch (NumberFormatException e) {
                    return "Enter an amount above 0, for example 1500 or 1500.50.";
                }
                return null;
            case VEHICLE_TEXT:
                if (t.chars().noneMatch(Character::isLetterOrDigit)) {
                    return "Enter a valid name.";
                }
                return null;
            default:
                return null;
        }
    }

    /**
     * Cleans the finished text before saving: no extra spaces, plates in capital letters.
     */
    public static String normalize(Kind kind, String text) {
        String t = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        if (kind == Kind.PLATE) {
            return t.toUpperCase(Locale.ROOT);
        }
        if (kind == Kind.EMAIL) {
            return t.toLowerCase(Locale.ROOT);
        }
        return t;
    }

    /**
     * True when two names are the same for a person: capital letters and extra spaces do not
     * matter. The make and model picked from NHTSA are compared with this, so "F-150  " matches
     * "f-150".
     */
    public static boolean sameName(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.trim().replaceAll("\\s+", " ").equalsIgnoreCase(b.trim().replaceAll("\\s+", " "));
    }

    private static int letterCount(String t) {
        return (int) t.chars().filter(Character::isLetter).count();
    }
}
