package fleetrent.service;


import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Finds a real photo of a car model through the free Wikipedia web service (no key needed) and
 * downloads it. Always call it from a background thread, never from the JavaFX UI thread.
 */
public class CarImageClient {
    private static final long CONNECT_TIMEOUT_SECONDS = 10;
    private static final long REQUEST_TIMEOUT_SECONDS = 30;
    private static final int HTTP_OK = 200;
    private static final int SEARCH_LIMIT = 6;
    private static final int THUMBNAIL_WIDTH = 800;
    private static final Logger LOG = Logger.getLogger(CarImageClient.class.getName());
    public static final String DEFAULT_API_URL = "https://en.wikipedia.org/w/api.php";
    public static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;

    private final String apiUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .proxy(ProxySelector.getDefault())
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build();

    /** Creates a car image client. */
    public CarImageClient() {
        this(DEFAULT_API_URL);
    }

    /**
     * The second constructor lets a test point the client at a fake local server.
     */
    public CarImageClient(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    /** Builds the Wikipedia search address for a make and model. */
    public String searchUrl(String make, String model) {
        return searchUrl(make, model, null);
    }

    /**
     * Builds the Wikipedia search address for a make, a model and (when known) the model year.
     */
    public String searchUrl(String make, String model, Integer year) {
        if (make == null || make.isBlank() || model == null || model.isBlank()) {
            throw new IllegalArgumentException("make and model are required");
        }
        String term = (year == null ? "" : year + " ") + make.trim() + " " + model.trim();
        return apiUrl + "?action=query&generator=search&gsrsearch=" + encode(term)
                + "&gsrnamespace=0&gsrlimit=" + SEARCH_LIMIT + "&prop=pageimages&piprop=thumbnail"
                + "&pithumbsize=" + THUMBNAIL_WIDTH + "&pilimit=" + SEARCH_LIMIT + "&format=json";
    }

    /**
     * Returns the address of a photo, or null when Wikipedia has no picture for that car.
     */
    public String findImageUrl(String make, String model) throws IOException, InterruptedException {
        return findImageUrl(make, model, null);
    }

    /**
     * Returns the address of a photo of exactly this car, or null. It searches with the year first
     * (for example "2021 Toyota Vios") and then without it. A page is used only when its title has
     * both the make and the model, so a photo of another car is never picked.
     */
    public String findImageUrl(String make, String model, Integer year)
            throws IOException, InterruptedException {
        List<Integer> years = new ArrayList<>();
        if (year != null && year > 0) {
            years.add(year);
        }
        years.add(null);
        for (Integer y : years) {
            String body = getText(searchUrl(make, model, y));
            try {
                String url = parseThumbnailUrl(body, make, model);
                if (url != null) {
                    return url;
                }
            } catch (IllegalArgumentException e) {
                throw new IOException("Wikipedia sent an answer we could not read: "
                        + e.getMessage(), e);
            }
        }
        return null;
    }

    /**
     * Downloads the picture. Throws IOException if it is too big or is not a picture.
     */
    public byte[] download(String imageUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header("User-Agent", "FleetRent-CPE314/1.0 (student project)")
                .GET()
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        LOG.info("[photo] GET " + imageUrl + " -> HTTP " + response.statusCode());
        if (response.statusCode() != HTTP_OK) {
            throw new IOException("Photo server answered with HTTP " + response.statusCode());
        }
        byte[] bytes = response.body();
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new IOException("The photo is too big.");
        }
        if (CarImageStore.extensionFromBytes(bytes) == null) {
            throw new IOException("The download was not a JPG, PNG, GIF or BMP picture.");
        }
        return bytes;
    }

    private String getText(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header("Accept", "application/json")
                .header("User-Agent", "FleetRent-CPE314/1.0 (student project)")
                .GET()
                .build();
        long start = System.currentTimeMillis();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        LOG.info("[photo] GET " + url + " -> HTTP " + response.statusCode()
                + " in " + (System.currentTimeMillis() - start) + " ms");
        if (response.statusCode() != HTTP_OK) {
            throw new IOException("Wikipedia answered with HTTP " + response.statusCode());
        }
        return response.body();
    }

    /**
     * Reads the JSON and returns the thumbnail of the best search hit that has one, or null. Throws
     * IllegalArgumentException if the text is not what we expect.
     */
    public static String parseThumbnailUrl(String json) {
        return parseThumbnailUrl(json, null, null);
    }

    /**
     * Same as above, and when a make and a model are given it only takes a page whose title has
     * both of them (spaces, dashes and capital letters do not matter).
     */
    public static String parseThumbnailUrl(String json, String make, String model) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("empty answer");
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("not valid JSON");
        }
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("answer is not an object");
        }
        JsonElement query = root.getAsJsonObject().get("query");
        if (query == null || !query.isJsonObject()) {
            return null; // Wikipedia leaves out "query" when nothing matched.
        }
        JsonElement pages = query.getAsJsonObject().get("pages");
        if (pages == null || !pages.isJsonObject()) {
            return null;
        }
        String best = null;
        int bestIndex = Integer.MAX_VALUE;
        for (Map.Entry<String, JsonElement> entry : pages.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject page = entry.getValue().getAsJsonObject();
            if (!titleMatches(page, make, model)) {
                continue;
            }
            JsonElement thumb = page.get("thumbnail");
            if (thumb == null || !thumb.isJsonObject() || !thumb.getAsJsonObject().has("source")) {
                continue;
            }
            String source = thumb.getAsJsonObject().get("source").getAsString();
            int index = page.has("index") && page.get("index").isJsonPrimitive()
                    ? page.get("index").getAsInt() : Integer.MAX_VALUE - 1;
            if (source != null && source.startsWith("http") && index < bestIndex) {
                best = source;
                bestIndex = index;
            }
        }
        return best;
    }

    // True when no make or model is asked for, or the page title has both of them.
    private static boolean titleMatches(JsonObject page, String make, String model) {
        if (make == null || model == null) {
            return true;
        }
        JsonElement title = page.get("title");
        if (title == null || !title.isJsonPrimitive()) {
            return false;
        }
        String t = squash(title.getAsString());
        return t.contains(squash(make)) && t.contains(squash(model));
    }

    // Lower case letters and digits only, so "F-150" and "f 150" are the same.
    private static String squash(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}0-9]", "");
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
