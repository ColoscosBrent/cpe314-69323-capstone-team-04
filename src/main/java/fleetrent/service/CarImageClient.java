package fleetrent.service;

import fleetrent.dto.CommonsFileDto;
import fleetrent.dto.WikipediaPageDto;

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
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Finds a real photo of a car model through the free Wikipedia web service (no key needed) and
 * downloads it. When the color of the vehicle is known it first looks on Wikimedia Commons (the
 * picture library of Wikipedia) for a photo of that model in that color. Always call it from a
 * background thread, never from the JavaFX UI thread.
 */
public class CarImageClient {
    private static final long CONNECT_TIMEOUT_SECONDS = 10;
    private static final long REQUEST_TIMEOUT_SECONDS = 30;
    private static final int HTTP_OK = 200;
    private static final int SEARCH_LIMIT = 6;
    private static final int THUMBNAIL_WIDTH = 800;
    private static final Logger LOG = Logger.getLogger(CarImageClient.class.getName());
    /** The address of the Wikipedia web service. */
    public static final String DEFAULT_API_URL = "https://en.wikipedia.org/w/api.php";
    /** The address of the Wikimedia Commons web service (the search by color). */
    public static final String DEFAULT_COMMONS_URL = "https://commons.wikimedia.org/w/api.php";
    /** The biggest picture that is downloaded, in bytes. */
    public static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final int COLOR_SEARCH_LIMIT = 20;
    private static final int MAX_PHOTOS_TO_LOOK_AT = 4;
    // A file with one of these words in its name does not show the whole vehicle.
    private static final List<String> NOT_THE_WHOLE_CAR = List.of("interior", "dashboard",
            "engine", "logo", "badge", "emblem", "wheel", "seat", "cockpit", "steering", "trunk",
            "detail");
    private static final List<String> PICTURE_KINDS = List.of("image/jpeg", "image/png");

    private final String apiUrl;
    // The address of the Commons web service, or null when the color search is switched off.
    private final String commonsUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .proxy(ProxySelector.getDefault())
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build();

    /** Creates a car image client. */
    public CarImageClient() {
        this(DEFAULT_API_URL, DEFAULT_COMMONS_URL);
    }

    /**
     * The second constructor lets a test point the client at a fake local server. The search by
     * color is switched off.
     */
    public CarImageClient(String apiUrl) {
        this(apiUrl, null);
    }

    /**
     * The third constructor also sets the address of the Commons web service for the search by
     * color (null switches that search off).
     */
    public CarImageClient(String apiUrl, String commonsUrl) {
        this.apiUrl = apiUrl;
        this.commonsUrl = commonsUrl;
    }

    /**
     * Builds the Commons search address for picture files of a make and model in one color, or
     * null when the search by color is switched off.
     */
    public String colorSearchUrl(String make, String model, String color) {
        if (make == null || make.isBlank() || model == null || model.isBlank()
                || color == null || color.isBlank()) {
            throw new IllegalArgumentException("make, model and color are required");
        }
        if (commonsUrl == null) {
            return null;
        }
        String term = make.trim() + " " + model.trim() + " " + color.trim() + " filetype:bitmap";
        return commonsUrl + "?action=query&generator=search&gsrsearch=" + encode(term)
                + "&gsrnamespace=6&gsrlimit=" + COLOR_SEARCH_LIMIT
                + "&prop=imageinfo&iiprop=url%7Cmime&iiurlwidth=" + THUMBNAIL_WIDTH
                + "&format=json";
    }

    /**
     * What one search by color found.
     *
     * @param named  the photo whose file name says the color, or null
     * @param others photos of the same make and model whose names do not say a color, the best
     *               hit first (their pixels must be checked before one is used)
     */
    public record ColorSearch(String named, List<String> others) {
    }

    /**
     * Asks Commons once for photos of this make and model in this color. Returns null when the
     * search by color is switched off.
     */
    public ColorSearch searchByColor(String make, String model, String color)
            throws IOException, InterruptedException {
        String url = colorSearchUrl(make, model, color);
        if (url == null) {
            return null;
        }
        try {
            String json = getText(url);
            return new ColorSearch(parseColorImageUrl(json, make, model, color),
                    parseModelPhotoUrls(json, make, model));
        } catch (IllegalArgumentException e) {
            throw new IOException("Wikimedia Commons sent an answer we could not read: "
                    + e.getMessage(), e);
        }
    }

    /**
     * Returns the address of a photo of this make and model in this color, or null when Commons
     * has none (or the search by color is switched off). A file is used only when its name has
     * the make, the model and the color word.
     */
    public String findColorImageUrl(String make, String model, String color)
            throws IOException, InterruptedException {
        ColorSearch found = searchByColor(make, model, color);
        return found == null ? null : found.named();
    }

    /**
     * Reads the Commons JSON and returns the pictures of the files that show the whole vehicle of
     * this make and model, the best hit first (at most a few). The color is not checked here.
     * Throws IllegalArgumentException if the text is not what we expect.
     */
    public static List<String> parseModelPhotoUrls(String json, String make, String model) {
        List<CommonsFileDto> files = new ArrayList<>();
        for (CommonsFileDto file : ApiMapper.readFiles(json)) {
            if (isPicture(file) && showsWholeVehicle(file.getTitle(), make, model)) {
                files.add(file);
            }
        }
        files.sort((a, b) -> Integer.compare(rank(a), rank(b)));
        List<String> urls = new ArrayList<>();
        for (CommonsFileDto file : files) {
            if (urls.size() < MAX_PHOTOS_TO_LOOK_AT) {
                urls.add(file.getImage().getThumbUrl());
            }
        }
        return urls;
    }

    private static int rank(CommonsFileDto file) {
        return file.getIndex() == null ? Integer.MAX_VALUE - 1 : file.getIndex();
    }

    // True when the file has a web picture (JPG or PNG) of the asked size.
    private static boolean isPicture(CommonsFileDto file) {
        CommonsFileDto.ImageInfo image = file.getImage();
        return image != null && image.getThumbUrl() != null
                && image.getThumbUrl().startsWith("http")
                && PICTURE_KINDS.contains(image.getMime());
    }

    /**
     * Reads the Commons JSON and returns the picture of the best file whose name has the make, the
     * model and the color, or null. Throws IllegalArgumentException if the text is not what we
     * expect.
     */
    public static String parseColorImageUrl(String json, String make, String model,
            String color) {
        String best = null;
        int bestIndex = Integer.MAX_VALUE;
        for (CommonsFileDto file : ApiMapper.readFiles(json)) {
            if (!isPicture(file) || !nameMatches(file.getTitle(), make, model, color)) {
                continue;
            }
            if (rank(file) < bestIndex) {
                best = file.getImage().getThumbUrl();
                bestIndex = rank(file);
            }
        }
        return best;
    }

    // True when the file name has the make and the model and does not say it shows only a part
    // of the vehicle.
    private static boolean showsWholeVehicle(String title, String make, String model) {
        if (title == null) {
            return false;
        }
        String squashed = squash(title);
        if (!squashed.contains(squash(make)) || !squashed.contains(squash(model))) {
            return false;
        }
        String lower = title.toLowerCase(Locale.ROOT);
        for (String word : NOT_THE_WHOLE_CAR) {
            if (lower.contains(word)) {
                return false;
            }
        }
        return true;
    }

    // True when the file shows the whole vehicle and its name has the color as a whole word (so
    // "red" does not match "Fred").
    private static boolean nameMatches(String title, String make, String model, String color) {
        if (!showsWholeVehicle(title, make, model)) {
            return false;
        }
        String lower = title.toLowerCase(Locale.ROOT);
        String wanted = color.trim().toLowerCase(Locale.ROOT);
        List<String> words = "gray".equals(wanted) || "grey".equals(wanted)
                ? List.of("gray", "grey") : List.of(wanted);
        for (String word : words) {
            if (Pattern.compile("(^|[^a-z])" + Pattern.quote(word) + "([^a-z]|$)").matcher(lower)
                    .find()) {
                return true;
            }
        }
        return false;
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
        String best = null;
        int bestIndex = Integer.MAX_VALUE;
        for (WikipediaPageDto page : ApiMapper.readPages(json)) {
            if (!titleMatches(page, make, model) || page.getThumbnail() == null
                    || page.getThumbnail().getSource() == null) {
                continue;
            }
            String source = page.getThumbnail().getSource();
            int index = page.getIndex() == null ? Integer.MAX_VALUE - 1 : page.getIndex();
            if (source.startsWith("http") && index < bestIndex) {
                best = source;
                bestIndex = index;
            }
        }
        return best;
    }

    // True when no make or model is asked for, or the page title has both of them.
    private static boolean titleMatches(WikipediaPageDto page, String make, String model) {
        if (make == null || model == null) {
            return true;
        }
        if (page.getTitle() == null) {
            return false;
        }
        String t = squash(page.getTitle());
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
