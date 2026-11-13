package fleetrent;

/**
 * One car model as returned by the NHTSA vPIC API (for example TOYOTA / Camry).
 */
public class VehicleModelInfo {
    private final String makeName;
    private final int modelId;
    private final String modelName;

    /** Creates a vehicle model info. */
    public VehicleModelInfo(String makeName, int modelId, String modelName) {
        this.makeName = makeName;
        this.modelId = modelId;
        this.modelName = modelName;
    }

    /** Returns the make name. */
    public String getMakeName() { return makeName; }
    /** Returns the model id. */
    public int getModelId() { return modelId; }
    /** Returns the model name. */
    public String getModelName() { return modelName; }

    @Override
    public String toString() {
        return makeName + " " + modelName;
    }
}
