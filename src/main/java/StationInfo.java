public class StationInfo {
    private final int id;
    private final String name;
    private final String format;
    private final String genre;
    private final String pls;

    public StationInfo(int id, String name, String format, String genre, String pls) {
        this.id = id;
        this.name = name;
        this.format = format;
        this.genre = genre;
        this.pls = pls;
    }

    public String getPls() {
        return pls;
    }

    public String getName() {
        return name;
    }

    public String getFormat() {
        return format;
    }

    public String getGenre() {
        return genre;
    }

    public int getId() {
        return id;
    }
}
