import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class App {
    private static Logger logger = Logger.getLogger(App.class.getName());
    // <a href="/Genre?name=Adult%20Alternative" onclick="return loadStationsByGenre('Adult Alternative', 2, 1);">Adult Alternative</a>
    private static Pattern regexPatternForGenreOnClick
            = Pattern.compile("[^\\(]+\\('([^']+)', (\\d+), (\\d+)\\);");

    private final String mainUrl;
    private final String saveFolder = "stations";
    private WebClient webClient;
    private Map<Integer, GenreInfo> genres;

    private App(String mainUrl) {
        this.mainUrl = mainUrl;
    }

    public static void main(String[] args) {
        App app = new App("http://www.shoutcast.com/");
        // "http://www.shoutcast.com/scradioinwinamp/"
        // "https://www.shoutcast.com/"
        try {
            app.parse();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Map<Integer, GenreInfo> collectGenres(HtmlPage page) {
        logger.log(Level.INFO, "Collecting genres...");
        HashMap genres = new <Integer, GenreInfo>HashMap();
        final List<?> anchors = page.getByXPath("//li[@class[contains(., '-genre')]]//a");
        for (Object item : anchors) {
            HtmlAnchor a = (HtmlAnchor) item;
            String onClickString = a.getOnClickAttribute();
            GenreInfo genreInfo = parseGenreInfo(onClickString);
            logger.log(Level.INFO, "* Found genre " + genreInfo.getName());
            genres.put(genreInfo.getId(), genreInfo);
        }
        return genres;
    }

    private static GenreInfo parseGenreInfo(String onClickString) {
        Matcher matcher = regexPatternForGenreOnClick.matcher(onClickString);
        GenreInfo genreInfo = null;
        while (matcher.find()) {
            genreInfo = new GenreInfo(
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)),
                    matcher.group(1)
            );
        }
        return genreInfo;
    }

    private void parse() throws IOException {
        logger.log(Level.INFO, "Start parsing {0}", mainUrl);
        webClient = new WebClient();
        try {
            final HtmlPage page = webClient.getPage(mainUrl + "scradioinwinamp");
            genres = collectGenres(page);
            try {
                for (int genreId : genres.keySet()) {
                    GenreInfo genreInfo = genres.get(genreId);
                    List<StationInfo> stations = getStationsByGenre(genreInfo);
                    genreInfo.setStations(stations);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } finally {
            webClient.close();
            webClient = null;
        }
    }

    private List<StationInfo> getStationsByGenre(GenreInfo genreInfo) throws Exception {
        logger.log(Level.INFO, "Scanning stations of genre " + genreInfo.getName());
        URL url = new URL(mainUrl + "Home/BrowseByGenre");
        WebRequest requestSettings = new WebRequest(url, HttpMethod.POST);

        requestSettings.setAdditionalHeader("Accept", "*/*");
        requestSettings.setAdditionalHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        requestSettings.setAdditionalHeader("Referer", "http://www.shoutcast.com/scradioinwinamp/");
        requestSettings.setAdditionalHeader("Accept-Language", "en-US,en;q=0.8");
        requestSettings.setAdditionalHeader("Accept-Encoding", "gzip,deflate,sdch");
        requestSettings.setAdditionalHeader("Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.3");
        requestSettings.setAdditionalHeader("X-Requested-With", "XMLHttpRequest");
        requestSettings.setAdditionalHeader("Cache-Control", "no-cache");
        requestSettings.setAdditionalHeader("Pragma", "no-cache");
        requestSettings.setAdditionalHeader("Origin", "http://www.shoutcast.com");

        requestSettings.setRequestBody("genrename=" + genreInfo.getName());

        Page redirectPage = webClient.getPage(requestSettings);
        String content = redirectPage.getWebResponse().getContentAsString();

        ArrayList<StationInfo> stations = new ArrayList<>();
        JSONArray jsonStations = new JSONObject("{'stations':" + content + "}").getJSONArray("stations");
        String targetFolder = getTargetFolder(genreInfo);
        for (int i = 0; i < jsonStations.length(); i++) {
            int id = jsonStations.getJSONObject(i).getInt("ID");
            String name = jsonStations.getJSONObject(i).getString("Name");
            String format = jsonStations.getJSONObject(i).getString("Format");
            String genre = jsonStations.getJSONObject(i).getString("Genre");
            String pls = getPls(id);

            if ((pls != null) && !pls.contains("numberofentries=0")) {
                logger.log(Level.INFO, "Adding station " + id + " " + name + " (" + genre + ")");
                StationInfo stationInfo = new StationInfo(id, name, format, genre, pls);
                stations.add(stationInfo);
                saveStation(stationInfo, targetFolder);
            } else {
                logger.log(Level.WARNING, "Skipping station " + id + " " + name);
            }
        }
        return stations;
    }

    private String getTargetFolder(GenreInfo genreInfo) {
        Path targetPath;
        if (genreInfo.getParentId() > 0) {
            targetPath = Paths.get(saveFolder, genres.get(genreInfo.getParentId()).getName(), genreInfo.getName());
        } else {
            targetPath = Paths.get(saveFolder, genreInfo.getName());
        }
        return targetPath.toString();
    }

    private void saveStation(StationInfo stationInfo, String targetFolder) throws IOException {
        String fileName = stationInfo.getName().replaceAll("[^a-zA-Z0-9.-]", "_") + ".pls";
        String fullPath = Paths.get(targetFolder, fileName).toString();
        File outFile = new File(fullPath);
        outFile.getParentFile().mkdirs();
        outFile.createNewFile();
        try (PrintWriter out = new PrintWriter(outFile)) {
            out.println(stationInfo.getPls());
        }
    }

    private String getPls(int id) {
        String pls = null;

        try {
            URL url = new URL("http://yp.shoutcast.com/sbin/tunein-station.pls?id=" + id);
            WebRequest requestSettings = new WebRequest(url, HttpMethod.GET);
            Page redirectPage = webClient.getPage(requestSettings);
            pls = redirectPage.getWebResponse().getContentAsString();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return pls;
    }
}
