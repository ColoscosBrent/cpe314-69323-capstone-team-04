package fleetrent.model;

import java.util.ArrayList;
import java.util.List;

/** What a CSV import did: how many rentals were saved, how many rows were skipped, and why. */
public class ImportResult {
    private int imported;
    private int skipped;
    private final List<String> errors = new ArrayList<>();

    /** Returns how many rentals were saved. */
    public int getImported() {
        return imported;
    }

    /** Returns how many rows were skipped. */
    public int getSkipped() {
        return skipped;
    }

    /** Returns one message for every skipped row. */
    public List<String> getErrors() {
        return errors;
    }

    /** Counts one more skipped row. */
    public void countSkipped() {
        skipped++;
    }

    /** Adds the number of rentals saved in one batch. */
    public void addImported(int count) {
        imported += count;
    }
}
