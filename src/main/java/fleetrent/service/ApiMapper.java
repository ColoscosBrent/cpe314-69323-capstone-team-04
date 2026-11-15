package fleetrent.service;

import fleetrent.dto.CommonsFileDto;
import fleetrent.dto.VehicleMakeDto;
import fleetrent.dto.VehicleModelDto;
import fleetrent.dto.VehicleModelInfo;
import fleetrent.dto.VpicResponse;
import fleetrent.dto.WikipediaPageDto;
import fleetrent.dto.WikipediaResponse;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns the JSON answers of NHTSA vPIC and Wikipedia into DTO objects, and the DTOs into the
 * objects the rest of the program uses. It has no network code, so it is easy to test with the
 * saved answers in src/test/resources.
 */
public final class ApiMapper {
    private static final Gson GSON = new Gson();

    private ApiMapper() {
    }

    /** Reads the outer shape of a vPIC answer. Throws IllegalArgumentException if it is bad. */
    public static VpicResponse readVpic(String json) {
        VpicResponse response = parse(json, VpicResponse.class);
        if (response == null || response.getResults() == null) {
            throw new IllegalArgumentException("no Results list in the answer");
        }
        return response;
    }

    /** Reads the makes of a vPIC answer. A row that cannot be read is skipped. */
    public static List<VehicleMakeDto> readMakes(VpicResponse response) {
        List<VehicleMakeDto> makes = new ArrayList<>();
        for (JsonElement row : response.getResults()) {
            VehicleMakeDto dto = row(row, VehicleMakeDto.class);
            if (dto != null && dto.getMakeName() != null && !dto.getMakeName().isBlank()) {
                makes.add(dto);
            }
        }
        return makes;
    }

    /** Reads the models of a vPIC answer. A row that cannot be read is skipped. */
    public static List<VehicleModelDto> readModels(VpicResponse response) {
        List<VehicleModelDto> models = new ArrayList<>();
        for (JsonElement row : response.getResults()) {
            VehicleModelDto dto = row(row, VehicleModelDto.class);
            if (dto != null && dto.getModelName() != null && !dto.getModelName().isBlank()) {
                models.add(dto);
            }
        }
        return models;
    }

    /** Sorted make names without repeats (the first spelling of a name is kept). */
    public static List<String> makeNames(List<VehicleMakeDto> makes) {
        TreeMap<String, String> byLower = new TreeMap<>();
        for (VehicleMakeDto make : makes) {
            String clean = make.getMakeName().trim();
            byLower.putIfAbsent(clean.toLowerCase(Locale.ROOT), clean);
        }
        return new ArrayList<>(byLower.values());
    }

    /** Makes the DTO of one model into the object the program uses. */
    public static VehicleModelInfo toModelInfo(VehicleModelDto dto) {
        String make = dto.getMakeName() == null ? "" : dto.getMakeName().trim();
        int id = dto.getModelId() == null ? 0 : dto.getModelId();
        return new VehicleModelInfo(make, id, dto.getModelName().trim());
    }

    /** Makes the DTOs into model objects, sorted by model name. */
    public static List<VehicleModelInfo> toModelInfos(List<VehicleModelDto> dtos) {
        List<VehicleModelInfo> models = new ArrayList<>();
        for (VehicleModelDto dto : dtos) {
            models.add(toModelInfo(dto));
        }
        models.sort(Comparator.comparing(VehicleModelInfo::getModelName,
                String.CASE_INSENSITIVE_ORDER));
        return models;
    }

    /**
     * Reads a Wikipedia search answer and returns its pages. The list is empty when nothing
     * matched. Throws IllegalArgumentException if the text is not what we expect.
     */
    public static List<WikipediaPageDto> readPages(String json) {
        WikipediaResponse response = parse(json, WikipediaResponse.class);
        List<WikipediaPageDto> pages = new ArrayList<>();
        if (response == null || response.getQuery() == null
                || response.getQuery().getPages() == null) {
            return pages;
        }
        for (Map.Entry<String, JsonElement> entry : response.getQuery().getPages().entrySet()) {
            WikipediaPageDto page = row(entry.getValue(), WikipediaPageDto.class);
            if (page != null) {
                pages.add(page);
            }
        }
        return pages;
    }

    /**
     * Reads a Wikimedia Commons file search answer and returns its files. The answer has the same
     * outer shape as the Wikipedia one. The list is empty when nothing matched. Throws
     * IllegalArgumentException if the text is not what we expect.
     */
    public static List<CommonsFileDto> readFiles(String json) {
        WikipediaResponse response = parse(json, WikipediaResponse.class);
        List<CommonsFileDto> files = new ArrayList<>();
        if (response == null || response.getQuery() == null
                || response.getQuery().getPages() == null) {
            return files;
        }
        for (Map.Entry<String, JsonElement> entry : response.getQuery().getPages().entrySet()) {
            CommonsFileDto file = row(entry.getValue(), CommonsFileDto.class);
            if (file != null) {
                files.add(file);
            }
        }
        return files;
    }

    private static <T> T parse(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("empty answer");
        }
        try {
            JsonElement root = com.google.gson.JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                throw new IllegalArgumentException("answer is not an object");
            }
            return GSON.fromJson(root, type);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("not valid JSON");
        }
    }

    // One row of a list. A row that is not an object or has a wrong type gives null.
    private static <T> T row(JsonElement element, Class<T> type) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        try {
            return GSON.fromJson(element, type);
        } catch (JsonParseException e) {
            return null;
        }
    }
}
