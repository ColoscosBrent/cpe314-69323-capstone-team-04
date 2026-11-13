package fleetrent.model;

import java.time.LocalDateTime;

/**
 * One CSV import in the history: which file, when, who did it, and how many rows were read, saved
 * and skipped.
 */
public record ImportBatch(int id, String fileName, String importedBy, LocalDateTime importedOn,
        int rowsRead, int rowsAdded, int rowsSkipped) {
}
