package fleetrent.dto;

import com.google.gson.annotations.SerializedName;

/** One row of the vPIC models answer, for example TOYOTA / Camry. */
public class VehicleModelDto {
    @SerializedName("Make_ID")
    private Integer makeId;
    @SerializedName("Make_Name")
    private String makeName;
    @SerializedName("Model_ID")
    private Integer modelId;
    @SerializedName("Model_Name")
    private String modelName;

    /** Returns the make id, or null when it is missing. */
    public Integer getMakeId() {
        return makeId;
    }

    /** Returns the make name, or null when it is missing. */
    public String getMakeName() {
        return makeName;
    }

    /** Returns the model id, or null when it is missing. */
    public Integer getModelId() {
        return modelId;
    }

    /** Returns the model name, or null when it is missing. */
    public String getModelName() {
        return modelName;
    }
}
