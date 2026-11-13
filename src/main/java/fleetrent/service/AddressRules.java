package fleetrent.service;

import java.util.regex.Pattern;

/**
 * An address is typed in three boxes (street address, city, country) and saved as one text:
 * "12 Rizal St, Cebu City, Philippines". It has no screen code, so it can be unit tested.
 */
public final class AddressRules {
    private static final int STREET_MIN = 3;
    private static final int CITY_MIN_LETTERS = 2;
    private static final Pattern STREET_OK = Pattern.compile("[\\p{L}0-9 .#/'\\-]+");
    private static final Pattern CITY_OK = Pattern.compile("\\p{L}[\\p{L} .'\\-]*");

    private AddressRules() {
    }

    /** Joins the street address, the city and the country with commas. */
    public static String compose(String street, String city, String country) {
        return clean(street) + ", " + clean(city) + ", " + clean(country);
    }

    /** Returns null when the street address is fine, otherwise a message. */
    public static String checkStreet(String street) {
        String t = clean(street);
        if (t.isEmpty()) {
            return "The street address is required.";
        }
        if (t.length() < STREET_MIN || !STREET_OK.matcher(t).matches()
                || t.chars().noneMatch(Character::isLetterOrDigit)) {
            return "Enter a street address, for example 12 Rizal St.";
        }
        return null;
    }

    /** Returns null when the city is fine, otherwise a message. */
    public static String checkCity(String city) {
        String t = clean(city);
        if (t.isEmpty()) {
            return "The city is required.";
        }
        long letters = t.chars().filter(Character::isLetter).count();
        if (!CITY_OK.matcher(t).matches() || letters < CITY_MIN_LETTERS) {
            return "Enter a city name (letters only).";
        }
        return null;
    }

    /** Returns null when the country is in the list, otherwise a message. */
    public static String checkCountry(String country) {
        if (country == null || country.isBlank()) {
            return "Choose the country.";
        }
        return Countries.byName(country) == null ? "Choose the country from the list." : null;
    }

    /**
     * Checks a saved address "street, city, country". Returns null when it is fine, otherwise a
     * message.
     */
    public static String checkSaved(String saved) {
        String t = saved == null ? "" : saved.trim();
        if (t.isEmpty()) {
            return "The address is required: street address, city and country.";
        }
        String[] parts = t.split(",", -1);
        if (parts.length < 3) {
            return "Enter the address as: street address, city and country.";
        }
        String message = checkCountry(parts[parts.length - 1]);
        if (message == null) {
            message = checkCity(parts[parts.length - 2]);
        }
        if (message == null) {
            StringBuilder street = new StringBuilder(parts[0]);
            for (int i = 1; i < parts.length - 2; i++) {
                street.append(',').append(parts[i]);
            }
            message = checkStreet(street.toString().replace(",", " "));
        }
        return message;
    }

    private static String clean(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ");
    }
}
