package fleetrent.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes one line of a CSV file. A value that has a comma, a quote or a line break is
 * put between double quotes, and a quote inside it is doubled, so "Reyes, Ana Santos" stays one
 * value.
 */
public final class CsvLine {
    private CsvLine() {
    }

    /**
     * Splits a line into its values. Throws IllegalArgumentException when a quote is not closed.
     */
    public static List<String> parse(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    current.append(ch);
                }
            } else if (ch == '"' && current.length() == 0) {
                quoted = true;
            } else if (ch == ',') {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("a quote (\") is not closed");
        }
        values.add(current.toString());
        return values;
    }

    /** Joins values into one line. A null value is written as nothing (a blank). */
    public static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(quote(values.get(i)));
        }
        return out.toString();
    }

    private static String quote(String value) {
        if (value == null) {
            return "";
        }
        boolean needs = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        return needs ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }
}
