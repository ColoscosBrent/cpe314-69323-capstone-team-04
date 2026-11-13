package fleetrent.service;

import fleetrent.model.Vehicle;
import fleetrent.model.VehicleFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CarImageClientTest {
    private HttpServer server;
    private String base;
    private int apiStatus;
    private String apiBody;
    private byte[] imageBytes;
    private String lastQuery;

    @TempDir
    Path temp;

    @BeforeEach
    void start() throws IOException {
        apiStatus = 200;
        imageBytes = CarImageStoreTest.PNG;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        apiBody = "{\"query\":{\"pages\":{\"11\":{\"index\":2,\"thumbnail\":{\"source\":\"" + base
                + "/other.png\"}},"
                + "\"22\":{\"index\":1,\"title\":\"Toyota Vios\",\"thumbnail\":{\"source\":\""
                        + base + "/car.png\"}}}}}";
        server.createContext("/w/api.php", ex -> {
            lastQuery = ex.getRequestURI().getRawQuery();
            byte[] b = apiBody.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(apiStatus, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.createContext("/car.png", ex -> {
            ex.sendResponseHeaders(200, imageBytes.length);
            ex.getResponseBody().write(imageBytes);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private CarImageClient client() {
        return new CarImageClient(base + "/w/api.php");
    }

    @Test
    void parseTakesTheBestRankedHitThatHasAPicture() {
        assertEquals(base + "/car.png", CarImageClient.parseThumbnailUrl(apiBody));
    }

    @Test
    void parseReturnsNullWhenNothingMatchedOrNoThumbnail() {
        assertNull(CarImageClient.parseThumbnailUrl("{\"batchcomplete\":\"\"}"));
        assertNull(CarImageClient.parseThumbnailUrl(
                "{\"query\":{\"pages\":{\"1\":{\"title\":\"x\"}}}}"));
    }

    @Test
    void parseRejectsBrokenAnswers() {
        assertThrows(IllegalArgumentException.class, () -> CarImageClient.parseThumbnailUrl(""));
        assertThrows(IllegalArgumentException.class, () ->
                CarImageClient.parseThumbnailUrl("<html>"));
        assertThrows(IllegalArgumentException.class, () ->
                CarImageClient.parseThumbnailUrl("[1,2]"));
    }

    @Test
    void searchUrlContainsMakeAndModelEncoded() {
        String url = client().searchUrl("Toyota", "Land Cruiser");
        assertTrue(url.contains("gsrsearch=Toyota%20Land%20Cruiser"));
        assertThrows(IllegalArgumentException.class, () -> client().searchUrl(" ", "Vios"));
        assertThrows(IllegalArgumentException.class, () -> client().searchUrl("Toyota", null));
    }

    @Test
    void findImageUrlTalksToTheServer() throws Exception {
        assertEquals(base + "/car.png", client().findImageUrl("Toyota", "Vios"));
        assertTrue(lastQuery.contains("gsrsearch=Toyota%20Vios"));
    }

    @Test
    void serverErrorBecomesIOException() {
        apiStatus = 500;
        assertThrows(IOException.class, () -> client().findImageUrl("Toyota", "Vios"));
    }

    @Test
    void downloadRejectsNonPictures() throws Exception {
        assertEquals(CarImageStoreTest.PNG.length, client().download(base + "/car.png").length);
        imageBytes = "<html>no</html>".getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> client().download(base + "/car.png"));
    }

    @Test
    void photoFinderSavesThePictureForTheVehicle() throws Exception {
        CarImageStore store = new CarImageStore(temp.resolve("pics"));
        PhotoFinder finder = new PhotoFinder(client(), store);
        Vehicle v = VehicleFactory.create(7, "SEDAN", "Toyota", "Vios", 2020, "AAA 111", "White",
                1500, 0, null, VehicleFactory.defaultCategoryPath("SEDAN"));
        assertTrue(finder.findAndSave(v));
        assertTrue(store.has(7));
    }

    @Test
    void photoFinderReturnsFalseWhenThereIsNoPicture() throws Exception {
        apiBody = "{\"batchcomplete\":\"\"}";
        CarImageStore store = new CarImageStore(temp.resolve("pics"));
        Vehicle v = VehicleFactory.create(8, "SUV", "Zzzz", "Qqqq", 2020, "BBB 222", "Black",
                2500, 0, null, VehicleFactory.defaultCategoryPath("SUV"));
        assertFalse(new PhotoFinder(client(), store).findAndSave(v));
        assertFalse(store.has(8));
    }
}
