package fleetrent;

import java.io.IOException;

// Puts the two photo helpers together: ask for the picture address, download it, save it.
public class PhotoFinder {
    private final CarImageClient client;
    private final CarImageStore store;

    public PhotoFinder(CarImageClient client, CarImageStore store) {
        this.client = client;
        this.store = store;
    }

    // Returns true when a photo was found and saved. Returns false when Wikipedia has no picture.
    // Throws IOException when the network fails. Run it on a background thread.
    public boolean findAndSave(Vehicle vehicle) throws IOException, InterruptedException {
        String url = client.findImageUrl(vehicle.getMake(), vehicle.getModel());
        if (url == null) {
            return false;
        }
        byte[] bytes = client.download(url);
        store.save(vehicle.getId(), bytes);
        return true;
    }

    public CarImageStore getStore() {
        return store;
    }
}
