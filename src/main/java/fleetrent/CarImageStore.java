package fleetrent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Keeps one picture file per vehicle in a folder (default: vehicle_images next to the database).
 * The file name is vehicle-<id>.<ext>, so the database needs no new column.
 */
public class CarImageStore {
    public static final String DEFAULT_FOLDER = "vehicle_images";
    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final List<String> EXTENSIONS = List.of("jpg", "png", "gif", "bmp");

    private final Path folder;

    /** Creates a car image store. */
    public CarImageStore() {
        this(Path.of(DEFAULT_FOLDER));
    }

    /** Creates a car image store. */
    public CarImageStore(Path folder) {
        this.folder = folder;
    }

    /**
     * The saved picture of a vehicle, or null if it has none.
     */
    public Path find(int vehicleId) {
        for (String ext : EXTENSIONS) {
            Path p = folder.resolve("vehicle-" + vehicleId + "." + ext);
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    /** Tells whether a picture is saved for the vehicle. */
    public boolean has(int vehicleId) {
        return find(vehicleId) != null;
    }

    /**
     * Saves downloaded bytes. The picture type is found from the bytes, not from a name.
     */
    public Path save(int vehicleId, byte[] bytes) throws IOException {
        String ext = extensionFromBytes(bytes);
        if (ext == null) {
            throw new IllegalArgumentException("That is not a JPG, PNG, GIF or BMP picture.");
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("The picture is bigger than 10 MB.");
        }
        Files.createDirectories(folder);
        delete(vehicleId);
        Path target = folder.resolve("vehicle-" + vehicleId + "." + ext);
        Files.write(target, bytes);
        return target;
    }

    /**
     * Copies a picture file the user chose.
     */
    public Path copyFrom(int vehicleId, Path source) throws IOException {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IllegalArgumentException("That picture file was not found.");
        }
        if (Files.size(source) > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("The picture is bigger than 10 MB.");
        }
        byte[] bytes = Files.readAllBytes(source);
        return save(vehicleId, bytes);
    }

    /** Deletes the saved picture of the vehicle, if there is one. */
    public void delete(int vehicleId) throws IOException {
        for (String ext : EXTENSIONS) {
            Files.deleteIfExists(folder.resolve("vehicle-" + vehicleId + "." + ext));
        }
    }

    /**
     * Looks at the first bytes of a file to see what kind of picture it is. Returns null if
     * unknown.
     */
    public static String extensionFromBytes(byte[] b) {
        if (b == null || b.length < 4) {
            return null;
        }
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) {
            return "jpg";
        }
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "png";
        }
        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F') {
            return "gif";
        }
        if (b[0] == 'B' && b[1] == 'M') {
            return "bmp";
        }
        return null;
    }

    /** Tells whether the file name ends like a picture file. */
    public static boolean isPictureName(String fileName) {
        String n = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                || n.endsWith(".gif") || n.endsWith(".bmp");
    }
}
