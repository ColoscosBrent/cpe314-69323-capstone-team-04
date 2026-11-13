package fleetrent.service;

import fleetrent.dto.VehicleModelInfo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.ProxySelector;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Calls the free NHTSA vPIC web service (no API key) and turns the JSON answer into Java objects.
 * Always call it from a background thread, never from the JavaFX UI thread.
 */
public class VpicClient {
    private static final long CONNECT_TIMEOUT_SECONDS = 10;
    private static final long REQUEST_TIMEOUT_SECONDS = 30;
    private static final int HTTP_OK = 200;
    private static final int LAST_UNSUPPORTED_YEAR = 1995;
    private static final Logger LOG = Logger.getLogger(VpicClient.class.getName());
    public static final String DEFAULT_BASE_URL = "https://vpic.nhtsa.dot.gov/api";

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .proxy(ProxySelector.getDefault())
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build();

    /** Creates the NHTSA vPIC client. */
    public VpicClient() {
        // The property fleetrent.vpicUrl lets a test or a demo point the app at another server.
        this(System.getProperty("fleetrent.vpicUrl", DEFAULT_BASE_URL));
    }

    /**
     * The second constructor lets a test point the client at a fake local server.
     */
    public VpicClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * All models of a make, for example "Toyota".
     */
    public List<VehicleModelInfo> fetchModels(String make) throws IOException,
            InterruptedException {
        return fetch(modelsUrl(make));
    }

    /**
     * Models of a make for one model year. vPIC only supports years after 1995.
     */
    public List<VehicleModelInfo> fetchModels(String make, int year) throws IOException,
            InterruptedException {
        return fetch(modelsUrl(make, year));
    }

    /** Builds the NHTSA address that lists the models. */
    public String modelsUrl(String make) {
        return baseUrl + "/vehicles/GetModelsForMake/" + encode(cleanMake(make)) + "?format=json";
    }

    /** Builds the NHTSA address that lists the models. */
    public String modelsUrl(String make, int year) {
        if (year <= LAST_UNSUPPORTED_YEAR) {
            throw new IllegalArgumentException("The API only has model years after 1995.");
        }
        return baseUrl + "/vehicles/GetModelsForMakeYear/make/" + encode(cleanMake(make))
                + "/modelyear/" + year + "?format=json";
    }

    /**
     * All makes that NHTSA lists for one vehicle type word: "car", "mpv" or "motorcycle".
     */
    public List<String> fetchMakes(String apiType) throws IOException, InterruptedException {
        String body = get(makesUrl(apiType));
        try {
            List<String> makes = parseMakes(body);
            LOG.info("[vPIC] parsed " + makes.size() + " makes");
            return makes;
        } catch (IllegalArgumentException e) {
            throw new IOException("NHTSA sent an answer we could not read: " + e.getMessage(), e);
        }
    }

    /**
     * Models of one make for one vehicle type word. year may be null (all years).
     */
    public List<VehicleModelInfo> fetchModels(String make, Integer year, String apiType)
            throws IOException, InterruptedException {
        return fetch(modelsUrl(make, year, apiType));
    }

    /** Builds the NHTSA address that lists the makes. */
    public String makesUrl(String apiType) {
        return baseUrl + "/vehicles/GetMakesForVehicleType/" + encode(cleanType(apiType))
                + "?format=json";
    }

    /** Builds the NHTSA address that lists the models. */
    public String modelsUrl(String make, Integer year, String apiType) {
        String url = baseUrl + "/vehicles/GetModelsForMakeYear/make/" + encode(cleanMake(make));
        if (year != null) {
            if (year <= LAST_UNSUPPORTED_YEAR) {
                throw new IllegalArgumentException("The API only has model years after 1995.");
            }
            url += "/modelyear/" + year;
        }
        return url + "/vehicleType/" + encode(cleanType(apiType)) + "?format=json";
    }

    private List<VehicleModelInfo> fetch(String url) throws IOException, InterruptedException {
        String body = get(url);
        try {
            List<VehicleModelInfo> models = parseModels(body);
            LOG.info("[vPIC] parsed " + models.size() + " models");
            return models;
        } catch (IllegalArgumentException e) {
            throw new IOException("NHTSA sent an answer we could not read: " + e.getMessage(), e);
        }
    }

    private String get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header("Accept", "application/json")
                .header("User-Agent", "FleetRent-CPE314/1.0 (student project)")
                .GET()
                .build();
        long start = System.currentTimeMillis();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        long ms = System.currentTimeMillis() - start;
        // This line is what you show the instructor as proof of a live call.
        LOG.info("[vPIC] GET " + url + " -> HTTP " + response.statusCode() + " in " + ms
                + " ms");
        if (response.statusCode() != HTTP_OK) {
            throw new IOException("NHTSA answered with HTTP " + response.statusCode());
        }
        return response.body();
    }

    /**
     * Turns the makes JSON into a sorted list of names without duplicates.
     */
    public static List<String> parseMakes(String json) {
        JsonArray results = resultsOf(json);
        java.util.TreeMap<String, String> byLower = new java.util.TreeMap<>();
        for (JsonElement item : results) {
            if (!item.isJsonObject()) {
                continue;
            }
            String name = text(item.getAsJsonObject(), "MakeName");
            if (name == null || name.isBlank()) {
                continue;
            }
            String clean = name.trim();
            byLower.putIfAbsent(clean.toLowerCase(java.util.Locale.ROOT), clean);
        }
        return new ArrayList<>(byLower.values());
    }

    private static String cleanType(String apiType) {
        if (apiType == null || apiType.isBlank()) {
            throw new IllegalArgumentException("A vehicle type is needed.");
        }
        return apiType.trim();
    }

    private static JsonArray resultsOf(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("empty answer");
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("not valid JSON");
        }
        if (!root.isJsonObject() || !root.getAsJsonObject().has("Results")
                || !root.getAsJsonObject().get("Results").isJsonArray()) {
            throw new IllegalArgumentException("no Results list in the answer");
        }
        return root.getAsJsonObject().getAsJsonArray("Results");
    }

    /**
     * Turns the JSON text into a list. Throws IllegalArgumentException if the text is not what we
     * expect.
     */
    public static List<VehicleModelInfo> parseModels(String json) {
        JsonArray results = resultsOf(json);
        List<VehicleModelInfo> models = new ArrayList<>();
        for (JsonElement item : results) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject o = item.getAsJsonObject();
            String name = text(o, "Model_Name");
            if (name == null || name.isBlank()) {
                continue;
            }
            String make = text(o, "Make_Name");
            models.add(new VehicleModelInfo(make == null ? "" : make.trim(), number(o, "Model_ID"),
                    name.trim()));
        }
        models.sort(Comparator.comparing(VehicleModelInfo::getModelName,
                String.CASE_INSENSITIVE_ORDER));
        return models;
    }

    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return (e == null || e.isJsonNull()) ? null : e.getAsString();
    }

    private static int number(JsonObject o, String key) {
        try {
            JsonElement e = o.get(key);
            return (e == null || e.isJsonNull()) ? 0 : e.getAsInt();
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static String cleanMake(String make) {
        if (make == null || make.isBlank()) {
            throw new IllegalArgumentException("Type a make first, for example Toyota.");
        }
        return make.trim();
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
