import java.util.List;

public class GenreInfo {
    private final int id;
    private final int parentId;
    private final String name;
    private List<StationInfo> stations;

    public GenreInfo(int id, int parentId, String name) {
        this.id = id;
        this.parentId = parentId;
        this.name = name;
    }

    public List<StationInfo> getStations() {
        return stations;
    }

    public void setStations(List<StationInfo> stations) {
        this.stations = stations;
    }

    public int getId() {
        return id;
    }

    public int getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

}
