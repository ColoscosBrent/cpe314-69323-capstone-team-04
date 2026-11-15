package fleetrent.service;

import fleetrent.model.Vehicle;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Puts the two photo helpers together: ask for the picture address, download it, save it.
 */
public class PhotoFinder {
    private static final Logger LOG = Logger.getLogger(PhotoFinder.class.getName());

    private final CarImageClient client;
    private final CarImageStore store;

    /** Creates a photo finder. */
    public PhotoFinder(CarImageClient client, CarImageStore store) {
        this.client = client;
        this.store = store;
    }

    /** What a photo search found. */
    public enum Found {
        /** No picture was found. */
        NONE,
        /** The usual photo of the model was saved (it may show another color). */
        USUAL,
        /** A photo in the color of the vehicle was saved. */
        IN_COLOR
    }

    /**
     * Returns true when a photo was found and saved (see search). Run it on a background thread.
     */
    public boolean findAndSave(Vehicle vehicle) throws IOException, InterruptedException {
        return search(vehicle) != Found.NONE;
    }

    /**
     * Looks for a photo and saves it. It looks first for a photo in the color of the vehicle;
     * when there is none it takes the usual photo of the model. Throws IOException when the
     * network fails. Run it on a background thread.
     */
    public Found search(Vehicle vehicle) throws IOException, InterruptedException {
        if (saveInColor(vehicle)) {
            return Found.IN_COLOR;
        }
        String url = client.findImageUrl(vehicle.getMake(), vehicle.getModel(),
                vehicle.getYear() > 0 ? vehicle.getYear() : null);
        if (url == null) {
            return Found.NONE;
        }
        byte[] bytes = client.download(url);
        store.save(vehicle.getId(), bytes);
        return Found.USUAL;
    }

    // Tries a photo in the color of the vehicle. A problem here is not an error: the usual photo
    // of the model is tried next.
    private boolean saveInColor(Vehicle vehicle) throws InterruptedException {
        String color = vehicle.getColor();
        if (color == null || color.isBlank()) {
            return false;
        }
        try {
            CarImageClient.ColorSearch found = client.searchByColor(vehicle.getMake(),
                    vehicle.getModel(), color);
            if (found == null) {
                return false;
            }
            // A file whose name says the color is taken at once.
            if (found.named() != null) {
                store.save(vehicle.getId(), client.download(found.named()));
                return true;
            }
            // The other photos of the model are looked at: the first one whose middle is mostly
            // that color is taken.
            for (String url : found.others()) {
                byte[] picture = client.download(url);
                if (PhotoColor.matches(picture, color)) {
                    store.save(vehicle.getId(), picture);
                    return true;
                }
            }
            return false;
        } catch (IOException | IllegalArgumentException e) {
            LOG.info("[photo] no " + color + " photo for " + vehicle + ": " + e.getMessage());
            return false;
        }
    }

    /** Returns the folder where the pictures are saved. */
    public CarImageStore getStore() {
        return store;
    }
}
