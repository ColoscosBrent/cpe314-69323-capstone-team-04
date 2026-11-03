package fleetrent;

// One car model as returned by the NHTSA vPIC API (for example TOYOTA / Camry).
public class VehicleModelInfo {
    private final String makeName;
    private final int modelId;
    private final String modelName;

    public VehicleModelInfo(String makeName, int modelId, String modelName) {
        this.makeName = makeName;
        this.modelId = modelId;
        this.modelName = modelName;
    }

    public String getMakeName() { return makeName; }
    public int getModelId() { return modelId; }
    public String getModelName() { return modelName; }

    @Override
    public String toString() {
        return makeName + " " + modelName;
    }
}
