package fleetrent.dto;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * The outer shape of every NHTSA vPIC answer: a count, a message and a list of result rows. The
 * rows are read later into {@link VehicleMakeDto} or {@link VehicleModelDto}.
 */
public class VpicResponse {
    @SerializedName("Count")
    private Integer count;
    @SerializedName("Message")
    private String message;
    @SerializedName("Results")
    private List<JsonElement> results;

    /** Returns the count that vPIC reports, or null when it is missing. */
    public Integer getCount() {
        return count;
    }

    /** Returns the message of vPIC, or null when it is missing. */
    public String getMessage() {
        return message;
    }

    /** Returns the raw result rows, or null when the answer has no Results list. */
    public List<JsonElement> getResults() {
        return results;
    }
}
