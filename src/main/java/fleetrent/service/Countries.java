package fleetrent.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The countries the app knows: the name for an address, and the calling code with the length of a
 * phone number for the phone number box. The Philippines is first because it is the default.
 */
public final class Countries {
    private static final int DEFAULT_MIN_DIGITS = 7;
    private static final int DEFAULT_MAX_DIGITS = 12;
    private static final int PH_DIGITS = 10;
    private static final int NANP_DIGITS = 10;
    private static final int EIGHT_DIGITS = 8;

    /** One country: its name, its calling code (without the plus) and its phone number length. */
    public record Country(String name, String dialCode, int minDigits, int maxDigits) {
        /** The text for a drop-down list, for example "Philippines (+63)". */
        public String label() {
            return name + " (+" + dialCode + ")";
        }

        @Override
        public String toString() {
            return label();
        }
    }

    /** The country that is picked first. */
    public static final String DEFAULT_NAME = "Philippines";

    private static final List<Country> ALL = build();

    private Countries() {
    }

    private static Country c(String name, String dial) {
        return new Country(name, dial, DEFAULT_MIN_DIGITS, DEFAULT_MAX_DIGITS);
    }

    private static Country c(String name, String dial, int min, int max) {
        return new Country(name, dial, min, max);
    }

    private static List<Country> build() {
        List<Country> list = new ArrayList<>();
        list.add(c(DEFAULT_NAME, "63", PH_DIGITS, PH_DIGITS));
        list.add(c("Australia", "61"));
        list.add(c("Bahrain", "973", EIGHT_DIGITS, EIGHT_DIGITS));
        list.add(c("Bangladesh", "880"));
        list.add(c("Brazil", "55"));
        list.add(c("Brunei", "673"));
        list.add(c("Cambodia", "855"));
        list.add(c("Canada", "1", NANP_DIGITS, NANP_DIGITS));
        list.add(c("China", "86"));
        list.add(c("Denmark", "45"));
        list.add(c("Egypt", "20"));
        list.add(c("France", "33"));
        list.add(c("Germany", "49"));
        list.add(c("Hong Kong", "852", EIGHT_DIGITS, EIGHT_DIGITS));
        list.add(c("India", "91"));
        list.add(c("Indonesia", "62"));
        list.add(c("Ireland", "353"));
        list.add(c("Israel", "972"));
        list.add(c("Italy", "39"));
        addFromJapan(list);
        return List.copyOf(list);
    }

    // The countries from Japan to Vietnam (the list is split in two to keep the methods short).
    private static void addFromJapan(List<Country> list) {
        list.add(c("Japan", "81"));
        list.add(c("Kenya", "254"));
        list.add(c("Kuwait", "965"));
        list.add(c("Macau", "853"));
        list.add(c("Malaysia", "60"));
        list.add(c("Mexico", "52"));
        list.add(c("Myanmar", "95"));
        list.add(c("Nepal", "977"));
        list.add(c("Netherlands", "31"));
        list.add(c("New Zealand", "64"));
        list.add(c("Nigeria", "234"));
        list.add(c("Norway", "47"));
        list.add(c("Oman", "968"));
        list.add(c("Pakistan", "92"));
        list.add(c("Qatar", "974"));
        list.add(c("Russia", "7"));
        list.add(c("Saudi Arabia", "966"));
        list.add(c("Singapore", "65", EIGHT_DIGITS, EIGHT_DIGITS));
        list.add(c("South Africa", "27"));
        list.add(c("South Korea", "82"));
        list.add(c("Spain", "34"));
        list.add(c("Sri Lanka", "94"));
        list.add(c("Sweden", "46"));
        list.add(c("Switzerland", "41"));
        list.add(c("Taiwan", "886"));
        list.add(c("Thailand", "66"));
        list.add(c("Turkey", "90"));
        list.add(c("United Arab Emirates", "971"));
        list.add(c("United Kingdom", "44"));
        list.add(c("United States", "1", NANP_DIGITS, NANP_DIGITS));
        list.add(c("Vietnam", "84"));
    }

    /** Every country, the Philippines first and the others in alphabetical order. */
    public static List<Country> all() {
        return ALL;
    }

    /** The default country (the Philippines). */
    public static Country defaultCountry() {
        return ALL.get(0);
    }

    /** The country with this name (capital letters do not matter), or null. */
    public static Country byName(String name) {
        if (name == null) {
            return null;
        }
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        for (Country country : ALL) {
            if (country.name().toLowerCase(Locale.ROOT).equals(wanted)) {
                return country;
            }
        }
        return null;
    }

    /** The first country with this calling code (without the plus), or null. */
    public static Country byDialCode(String dialCode) {
        for (Country country : ALL) {
            if (country.dialCode().equals(dialCode)) {
                return country;
            }
        }
        return null;
    }
}
