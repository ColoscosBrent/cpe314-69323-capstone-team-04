package fleetrent.service;

import fleetrent.dto.VehicleModelInfo;
import fleetrent.model.Motorcycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VpicClientTest {
    private static final String SAMPLE = "{\"Count\":3,\"Message\":\"Response returned successfully\","
            + "\"SearchCriteria\":\"Make:toyota\",\"Results\":["
            + "{\"Make_ID\":448,\"Make_Name\":\"TOYOTA\",\"Model_ID\":2208,\"Model_Name\":\"Corolla\"},"
            + "{\"Make_ID\":448,\"Make_Name\":\"TOYOTA\",\"Model_ID\":2209,\"Model_Name\":\"camry\"},"
            + "{\"Make_ID\":448,\"Make_Name\":\"TOYOTA\",\"Model_ID\":2210,\"Model_Name\":\"4Runner\"}]}";

    private HttpServer server;
    private int status;
    private String body;
    private String lastPath;

    @BeforeEach
    void startFakeServer() throws IOException {
        status = 200;
        body = SAMPLE;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath = exchange.getRequestURI().toString();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopFakeServer() {
        server.stop(0);
    }

    private VpicClient client() {
        return new VpicClient("http://127.0.0.1:" + server.getAddress().getPort() + "/api");
    }

    @Test
    void parseModelsReadsEveryResultAndSortsByName() {
        List<VehicleModelInfo> models = VpicClient.parseModels(SAMPLE);
        assertEquals(3, models.size());
        assertEquals("4Runner", models.get(0).getModelName());
        assertEquals("camry", models.get(1).getModelName());
        assertEquals("TOYOTA", models.get(0).getMakeName());
        assertEquals(2208, models.get(2).getModelId());
    }

    @Test
    void parseModelsGivesEmptyListWhenResultsIsEmpty() {
        assertTrue(VpicClient.parseModels("{\"Count\":0,\"Results\":[]}").isEmpty());
    }

    @Test
    void parseModelsSkipsEntriesWithoutAModelName() {
        String json = "{\"Results\":[{\"Model_ID\":1},{\"Model_Name\":null},{\"Model_Name\":\"  \"},"
                + "{\"Make_Name\":\"FORD\",\"Model_ID\":5,\"Model_Name\":\"Everest\"}]}";
        List<VehicleModelInfo> models = VpicClient.parseModels(json);
        assertEquals(1, models.size());
        assertEquals("Everest", models.get(0).getModelName());
    }

    @Test
    void parseModelsRejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> VpicClient.parseModels(null));
        assertThrows(IllegalArgumentException.class, () -> VpicClient.parseModels("   "));
        assertThrows(IllegalArgumentException.class, () -> VpicClient.parseModels("<html>oops</html>"));
        assertThrows(IllegalArgumentException.class, () -> VpicClient.parseModels("{\"Count\":1}"));
        assertThrows(IllegalArgumentException.class, () -> VpicClient.parseModels("[1,2,3]"));
    }

    @Test
    void urlIsBuiltAndEncodedCorrectly() {
        VpicClient c = new VpicClient();
        assertEquals("https://vpic.nhtsa.dot.gov/api/vehicles/GetModelsForMake/Toyota?format=json",
                c.modelsUrl(" Toyota "));
        assertEquals("https://vpic.nhtsa.dot.gov/api/vehicles/GetModelsForMake/Land%20Rover?format=json",
                c.modelsUrl("Land Rover"));
        assertEquals("https://vpic.nhtsa.dot.gov/api/vehicles/GetModelsForMakeYear/make/Honda/modelyear/2020?format=json",
                c.modelsUrl("Honda", 2020));
    }

    @Test
    void blankMakeAndOldYearAreRejectedBeforeAnyCall() {
        VpicClient c = new VpicClient();
        assertThrows(IllegalArgumentException.class, () -> c.modelsUrl(""));
        assertThrows(IllegalArgumentException.class, () -> c.modelsUrl(null));
        assertThrows(IllegalArgumentException.class, () -> c.modelsUrl("Toyota", 1995));
    }

    @Test
    void fetchModelsCallsTheServerAndParsesTheAnswer() throws Exception {
        List<VehicleModelInfo> models = client().fetchModels("Toyota");
        assertEquals(3, models.size());
        assertEquals("/api/vehicles/GetModelsForMake/Toyota?format=json", lastPath);
    }

    @Test
    void fetchModelsWithYearUsesTheYearEndpoint() throws Exception {
        client().fetchModels("Toyota", 2021);
        assertEquals("/api/vehicles/GetModelsForMakeYear/make/Toyota/modelyear/2021?format=json", lastPath);
    }

    @Test
    void serverErrorBecomesAnIOException() {
        status = 500;
        body = "error";
        assertThrows(IOException.class, () -> client().fetchModels("Toyota"));
    }

    @Test
    void unreadableAnswerBecomesAnIOException() {
        body = "not json at all";
        assertThrows(IOException.class, () -> client().fetchModels("Toyota"));
    }

    @Test
    void unreachableServerBecomesAnIOException() {
        server.stop(0);
        assertThrows(IOException.class, () -> client().fetchModels("Toyota"));
    }

    private static final String MAKES = "{\"Count\":3,\"Results\":["
            + "{\"MakeId\":474,\"MakeName\":\"HONDA\",\"VehicleTypeId\":2,\"VehicleTypeName\":\"Motorcycle\"},"
            + "{\"MakeId\":1,\"MakeName\":\"Aprilia\",\"VehicleTypeId\":2,\"VehicleTypeName\":\"Motorcycle\"},"
            + "{\"MakeId\":2,\"MakeName\":\"honda\",\"VehicleTypeId\":2,\"VehicleTypeName\":\"Motorcycle\"}]}";

    @Test
    void parseMakesSortsAndRemovesDuplicates() {
        List<String> makes = VpicClient.parseMakes(MAKES);
        assertEquals(List.of("Aprilia", "HONDA"), makes);
    }

    @Test
    void parseMakesRejectsBadJson() {
        assertThrows(IllegalArgumentException.class, () -> VpicClient.parseMakes("nope"));
    }

    @Test
    void fetchMakesUsesTheVehicleTypeUrl() throws Exception {
        body = MAKES;
        List<String> makes = client().fetchMakes("motorcycle");
        assertEquals(2, makes.size());
        assertTrue(lastPath.startsWith("/api/vehicles/GetMakesForVehicleType/motorcycle"));
    }

    @Test
    void modelsUrlWithYearAndTypeHasBothParts() {
        String url = client().modelsUrl("Honda", 2020, "motorcycle");
        assertTrue(url.contains("/make/Honda/modelyear/2020/vehicleType/motorcycle"));
    }

    @Test
    void modelsUrlWithoutYearSkipsTheYear() {
        String url = client().modelsUrl("Honda", null, "mpv");
        assertTrue(url.contains("/make/Honda/vehicleType/mpv"));
        assertTrue(!url.contains("modelyear"));
    }

    @Test
    void modelsUrlRejectsOldYearAndMissingType() {
        assertThrows(IllegalArgumentException.class, () -> client().modelsUrl("Honda", 1990, "car"));
        assertThrows(IllegalArgumentException.class, () -> client().modelsUrl("Honda", null, " "));
    }

    @Test
    void fetchModelsByTypeReadsTheList() throws Exception {
        List<VehicleModelInfo> models = client().fetchModels("Toyota", 2020, "car");
        assertEquals(3, models.size());
        assertTrue(lastPath.contains("/vehicleType/car"));
    }

    @Test
    void fetchMakesThrowsOnServerError() {
        status = 500;
        assertThrows(IOException.class, () -> client().fetchMakes("car"));
    }

    // Simone: edge cases for the makes answer.
    @Test
    void parseMakesGivesEmptyListWhenResultsIsEmpty() {
        assertTrue(VpicClient.parseMakes("{\"Count\":0,\"Results\":[]}").isEmpty());
    }

    @Test
    void parseMakesSkipsItemsWithoutAMakeName() {
        String json = "{\"Results\":[{\"MakeId\":1},{\"MakeName\":null},{\"MakeName\":\"  \"},"
                + "{\"MakeName\":\"Toyota\"}]}";
        assertEquals(List.of("Toyota"), VpicClient.parseMakes(json));
    }

    @Test
    void parseMakesRejectsAnAnswerWithoutResults() {
        assertThrows(IllegalArgumentException.class, () -> VpicClient.parseMakes("{\"Count\":0}"));
    }
}
