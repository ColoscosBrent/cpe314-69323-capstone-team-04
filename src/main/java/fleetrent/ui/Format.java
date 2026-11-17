package fleetrent.ui;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javafx.scene.control.DatePicker;
import javafx.util.StringConverter;

// One way to write dates, money and miles on every screen, so a number looks the same in a table,
// in a list and in the date boxes.
final class Format {
    // The peso sign.
    private static final String CURRENCY = "₱";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy",
            Locale.ENGLISH);

    private Format() {
    }

    // A date like 14 Nov 2026. Null gives an empty text.
    static String date(LocalDate date) {
        return date == null ? "" : date.format(DATE);
    }

    // Money with the peso sign and thousands separators, like ₱1,500.00.
    static String money(double amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return CURRENCY + format.format(amount);
    }

    // A distance with thousands separators, like 24,891,788 mi.
    static String miles(long miles) {
        return NumberFormat.getIntegerInstance(Locale.US).format(miles) + " mi";
    }

    // A whole number with thousands separators.
    static String number(long value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    // Makes a date box show and read dates the same way as the tables (14 Nov 2026).
    static void styleDatePicker(DatePicker picker) {
        picker.setConverter(new StringConverter<LocalDate>() {
            @Override
            public String toString(LocalDate value) {
                return date(value);
            }

            @Override
            public LocalDate fromString(String text) {
                try {
                    return text == null || text.isBlank() ? null : LocalDate.parse(text.trim(),
                            DATE);
                } catch (java.time.format.DateTimeParseException e) {
                    return null;
                }
            }
        });
    }
}
