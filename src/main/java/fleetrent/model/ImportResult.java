package fleetrent.model;

import java.util.ArrayList;
import java.util.List;

/** What a CSV import did: how many rentals were saved, how many rows were skipped, and why. */
public class ImportResult {
    private int imported;
    private int skipped;
    private final List<String> errors = new ArrayList<>();
    private int batchId;
    private boolean cancelled;
    private int rentalsBefore;

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

    /** Returns the id of this import in the Import history (0 when it was not saved there). */
    public int getBatchId() {
        return batchId;
    }

    /** Tells whether the user stopped the import before the end of the file. */
    public boolean isCancelled() {
        return cancelled;
    }

    /** Remembers that the user stopped the import. */
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    /** Remembers how many rentals the app had before this import started. */
    public void setRentalsBefore(int rentalsBefore) {
        this.rentalsBefore = rentalsBefore;
    }

    /** Returns how many rentals the app has after this import: the old ones and the new ones. */
    public int getTotalRentals() {
        return rentalsBefore + imported;
    }

    /** Remembers the id of this import in the Import history. */
    public void setBatchId(int batchId) {
        this.batchId = batchId;
    }
}
