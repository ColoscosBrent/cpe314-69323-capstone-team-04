package fleetrent.service;

import fleetrent.model.Vehicle;
import fleetrent.model.VehicleFactory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// The photo search by color: a saved Wikimedia Commons answer (src/test/resources/commons) is
// read, and a fake local server stands in for Commons and Wikipedia, so no internet is needed.
class ColorPhotoTest {
    private static final byte[] RED_PHOTO = withTail(CarImageStoreTest.PNG, (byte) 1);
    private static final byte[] USUAL_PHOTO = withTail(CarImageStoreTest.PNG, (byte) 2);

    private HttpServer server;
    private String base;
    private int commonsStatus;
    private String commonsBody;
    private String commonsQuery;
    private int commonsCalls;

    @TempDir
    Path temp;

    private static byte[] withTail(byte[] picture, byte last) {
        byte[] copy = java.util.Arrays.copyOf(picture, picture.length + 1);
        copy[picture.length] = last;
        return copy;
    }

    private static String saved(String name) throws IOException {
        try (InputStream in = ColorPhotoTest.class.getResourceAsStream("/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @BeforeEach
    void start() throws IOException {
        commonsStatus = 200;
        commonsCalls = 0;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        commonsBody = "{\"query\":{\"pages\":{\"5\":{\"index\":1,\"title\":\"File:Toyota Vios "
                + "red.jpg\",\"imageinfo\":[{\"thumburl\":\"" + base + "/red.png\",\"mime\":"
                + "\"image/jpeg\"}]}}}}";
        server.createContext("/commons/api.php", ex -> {
            commonsCalls++;
            commonsQuery = ex.getRequestURI().getRawQuery();
            byte[] b = commonsBody.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(commonsStatus, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.createContext("/w/api.php", ex -> {
            byte[] b = ("{\"query\":{\"pages\":{\"22\":{\"index\":1,\"title\":\"Toyota Vios\","
                    + "\"thumbnail\":{\"source\":\"" + base + "/usual.png\"}}}}}")
                    .getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.createContext("/red.png", ex -> {
            ex.sendResponseHeaders(200, RED_PHOTO.length);
            ex.getResponseBody().write(RED_PHOTO);
            ex.close();
        });
        server.createContext("/usual.png", ex -> {
            ex.sendResponseHeaders(200, USUAL_PHOTO.length);
            ex.getResponseBody().write(USUAL_PHOTO);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private CarImageClient client() {
        return new CarImageClient(base + "/w/api.php", base + "/commons/api.php");
    }

    private static Vehicle vios(int id, String color) {
        return VehicleFactory.create(id, "SEDAN", "Toyota", "Vios", 2020, "AAA 11" + id, color,
                1500, 0, null, VehicleFactory.defaultCategoryPath("SEDAN"));
    }

    @Test
    void theSavedCommonsAnswerGivesTheWholeCarInThatColor() throws IOException {
        String json = saved("commons/search-toyota-camry-red.json");
        // Not the seats (interior), not "Fred's", not the Corolla, not the PDF: the red Camry.
        assertEquals("https://upload.example.org/thumb/camry-red.jpg",
                CarImageClient.parseColorImageUrl(json, "Toyota", "Camry", "Red"));
        assertNull(CarImageClient.parseColorImageUrl(json, "Toyota", "Camry", "Blue"));
        assertNull(CarImageClient.parseColorImageUrl(json, "Honda", "Civic", "Red"));
    }

    @Test
    void grayAndGreyAreTheSameColor() {
        String json = "{\"query\":{\"pages\":{\"1\":{\"index\":1,\"title\":\"File:Honda Civic "
                + "grey.jpg\",\"imageinfo\":[{\"thumburl\":\"https://x/grey.jpg\",\"mime\":"
                + "\"image/jpeg\"}]}}}}";
        assertEquals("https://x/grey.jpg",
                CarImageClient.parseColorImageUrl(json, "Honda", "Civic", "Gray"));
    }

    @Test
    void anAnswerWithNoFilesOrABrokenOneIsHandled() {
        assertNull(CarImageClient.parseColorImageUrl("{\"batchcomplete\":\"\"}", "Toyota",
                "Vios", "Red"));
        assertThrows(IllegalArgumentException.class,
                () -> CarImageClient.parseColorImageUrl("<html>", "Toyota", "Vios", "Red"));
    }

    @Test
    void theColorSearchAsksCommonsForPictureFilesOfThatColor() throws Exception {
        assertEquals(base + "/red.png", client().findColorImageUrl("Toyota", "Vios", "Red"));
        assertTrue(commonsQuery.contains("gsrsearch=Toyota%20Vios%20Red%20filetype%3Abitmap"));
        assertTrue(commonsQuery.contains("gsrnamespace=6"));
        assertTrue(commonsQuery.contains("prop=imageinfo"));
        assertThrows(IllegalArgumentException.class,
                () -> client().colorSearchUrl("Toyota", "Vios", " "));
    }

    @Test
    void aClientWithoutCommonsNeverSearchesByColor() throws Exception {
        CarImageClient plain = new CarImageClient(base + "/w/api.php");
        assertNull(plain.colorSearchUrl("Toyota", "Vios", "Red"));
        assertNull(plain.findColorImageUrl("Toyota", "Vios", "Red"));
        assertEquals(0, commonsCalls);
    }

    @Test
    void thePhotoInTheColorOfTheVehicleIsSavedFirst() throws Exception {
        CarImageStore store = new CarImageStore(temp.resolve("pics"));
        assertTrue(new PhotoFinder(client(), store).findAndSave(vios(1, "Red")));
        assertArrayEquals(RED_PHOTO, Files.readAllBytes(store.find(1)));
    }

    @Test
    void withNoPhotoInThatColorTheUsualPhotoIsSaved() throws Exception {
        CarImageStore store = new CarImageStore(temp.resolve("pics"));
        // Commons only has a red one, so a blue Vios gets the usual Wikipedia photo.
        assertTrue(new PhotoFinder(client(), store).findAndSave(vios(2, "Blue")));
        assertArrayEquals(USUAL_PHOTO, Files.readAllBytes(store.find(2)));
    }

    @Test
    void aPhotoWithNoColorInItsNameIsTakenWhenItsPixelsHaveThatColor() throws Exception {
        byte[] blueCar = PhotoColorTest.png(PhotoColorTest.photo(new java.awt.Color(47, 109, 240),
                new java.awt.Color(120, 120, 124)));
        byte[] redCar = PhotoColorTest.png(PhotoColorTest.photo(new java.awt.Color(205, 30, 45),
                new java.awt.Color(120, 120, 124)));
        server.createContext("/one.png", ex -> {
            ex.sendResponseHeaders(200, blueCar.length);
            ex.getResponseBody().write(blueCar);
            ex.close();
        });
        server.createContext("/two.png", ex -> {
            ex.sendResponseHeaders(200, redCar.length);
            ex.getResponseBody().write(redCar);
            ex.close();
        });
        // Two photos of the Vios, and neither file name says a color.
        commonsBody = "{\"query\":{\"pages\":{\"7\":{\"index\":1,\"title\":\"File:2020 Toyota "
                + "Vios front.jpg\",\"imageinfo\":[{\"thumburl\":\"" + base + "/one.png\","
                + "\"mime\":\"image/png\"}]},\"8\":{\"index\":2,\"title\":\"File:Toyota Vios "
                + "2021.jpg\",\"imageinfo\":[{\"thumburl\":\"" + base + "/two.png\",\"mime\":"
                + "\"image/png\"}]}}}}";
        CarImageStore store = new CarImageStore(temp.resolve("pics"));
        PhotoFinder finder = new PhotoFinder(client(), store);
        // The red Vios gets the second photo (the first one is blue), the blue Vios the first.
        assertTrue(finder.findAndSave(vios(5, "Red")));
        assertArrayEquals(redCar, Files.readAllBytes(store.find(5)));
        assertTrue(finder.findAndSave(vios(6, "Blue")));
        assertArrayEquals(blueCar, Files.readAllBytes(store.find(6)));
        // No photo is green, so the green Vios gets the usual Wikipedia photo.
        assertTrue(finder.findAndSave(vios(7, "Green")));
        assertArrayEquals(USUAL_PHOTO, Files.readAllBytes(store.find(7)));
    }

    @Test
    void theSearchTellsWhetherThePhotoIsInTheColorOfTheVehicle() throws Exception {
        CarImageStore store = new CarImageStore(temp.resolve("pics"));
        PhotoFinder finder = new PhotoFinder(client(), store);
        assertEquals(PhotoFinder.Found.IN_COLOR, finder.search(vios(8, "Red")));
        assertEquals(PhotoFinder.Found.USUAL, finder.search(vios(9, "Blue")));
        // A vehicle with no color skips the search by color.
        int before = commonsCalls;
        assertEquals(PhotoFinder.Found.USUAL, finder.search(vios(10, " ")));
        assertEquals(before, commonsCalls);
        // A make and model that Wikipedia has no picture of.
        server.createContext("/empty/api.php", ex -> {
            byte[] b = "{\"batchcomplete\":\"\"}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        assertEquals(PhotoFinder.Found.NONE, new PhotoFinder(new CarImageClient(base
                + "/empty/api.php"), store).search(vios(11, "Red")));
    }

    @Test
    void thePhotosToLookAtAreTheWholeCarsOfThatModelBestHitFirst() throws IOException {
        String json = saved("commons/search-toyota-camry-red.json");
        // Not the seats, not the Corolla, not the PDF, not the file without a picture.
        assertEquals(java.util.List.of("https://upload.example.org/thumb/camry-fred.jpg",
                "https://upload.example.org/thumb/camry-red.jpg"),
                CarImageClient.parseModelPhotoUrls(json, "Toyota", "Camry"));
        assertTrue(CarImageClient.parseModelPhotoUrls(json, "Honda", "Civic").isEmpty());
    }

    @Test
    void aProblemInTheColorSearchFallsBackToTheUsualPhoto() throws Exception {
        commonsStatus = 500;
        CarImageStore store = new CarImageStore(temp.resolve("pics"));
        assertTrue(new PhotoFinder(client(), store).findAndSave(vios(3, "Red")));
        assertArrayEquals(USUAL_PHOTO, Files.readAllBytes(store.find(3)));
        commonsStatus = 200;
        commonsBody = "not json";
        assertTrue(new PhotoFinder(client(), store).findAndSave(vios(4, "Red")));
        assertArrayEquals(USUAL_PHOTO, Files.readAllBytes(store.find(4)));
    }
}
