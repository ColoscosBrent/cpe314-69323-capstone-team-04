package fleetrent.dto;

import com.google.gson.annotations.SerializedName;

/** One row of the vPIC makes answer, for example 474 / HONDA. */
public class VehicleMakeDto {
    @SerializedName("MakeId")
    private Integer makeId;
    @SerializedName("MakeName")
    private String makeName;

    /** Returns the make id, or null when it is missing. */
    public Integer getMakeId() {
        return makeId;
    }

    /** Returns the make name, or null when it is missing. */
    public String getMakeName() {
        return makeName;
    }
}
