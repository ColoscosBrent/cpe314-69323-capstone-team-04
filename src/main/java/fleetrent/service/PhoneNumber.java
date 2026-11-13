package fleetrent.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A phone number is chosen with a country (the calling code) and typed without it. It is saved as
 * one text: "+63 9171234567". It has no screen code, so it can be unit tested.
 */
public final class PhoneNumber {
    private static final Pattern SAVED = Pattern.compile("\\+(\\d{1,4}) (\\d+)");

    private PhoneNumber() {
    }

    /** Keeps the digits only and drops one leading 0 (people type 0917... for +63 917...). */
    public static String digitsOf(String typed) {
        String digits = typed == null ? "" : typed.replaceAll("[^0-9]", "");
        return digits.startsWith("0") ? digits.substring(1) : digits;
    }

    /** Joins the calling code and the digits: "+63 9171234567". */
    public static String compose(Countries.Country country, String typed) {
        return "+" + country.dialCode() + " " + digitsOf(typed);
    }

    /**
     * Returns null when the typed number fits the country, otherwise a message.
     */
    public static String check(Countries.Country country, String typed) {
        if (country == null) {
            return "Choose the country of the phone number.";
        }
        String digits = digitsOf(typed);
        if (digits.isEmpty()) {
            return "This field is required.";
        }
        if (digits.length() < country.minDigits() || digits.length() > country.maxDigits()) {
            return country.minDigits() == country.maxDigits()
                    ? "A number in " + country.name() + " has " + country.minDigits() + " digits."
                    : "A number in " + country.name() + " has " + country.minDigits() + " to "
                            + country.maxDigits() + " digits.";
        }
        if (country.name().equals(Countries.DEFAULT_NAME) && digits.charAt(0) != '9') {
            return "A mobile number in the Philippines starts with 9 (for example 917 123 4567).";
        }
        return null;
    }

    /**
     * Checks a saved text like "+63 9171234567". Returns null when it is fine, otherwise a message.
     */
    public static String checkSaved(String saved) {
        Matcher m = SAVED.matcher(saved == null ? "" : saved.trim());
        if (!m.matches()) {
            return "Enter the phone number with a country, for example +63 9171234567.";
        }
        Countries.Country country = Countries.byDialCode(m.group(1));
        if (country == null) {
            return "That calling code is not in the country list.";
        }
        return check(country, m.group(2));
    }
}
