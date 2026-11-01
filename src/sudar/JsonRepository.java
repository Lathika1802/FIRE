package sudar;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Reads data/sudar-data.json. Changes live only in memory (this is the "offline" mode). */
public class JsonRepository implements Repository {
    private final Path file;
    public JsonRepository(Path file) { this.file = file; }

    @Override public String name() { return "JSON file (" + file + ")"; }
    @Override public boolean persistent() { return false; }
    @Override public void saveAll(Dataset data) { /* nothing to persist */ }

    @SuppressWarnings("unchecked")
    @Override public Dataset load() throws Exception {
        Map<String, Object> root = (Map<String, Object>) MiniJson.parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        Dataset d = new Dataset();
        d.town = (String) root.get("town");
        for (Object o : (List<Object>) root.get("locations")) {
            Map<String, Object> m = (Map<String, Object>) o;
            d.locations.put((String) m.get("id"), new Location((String) m.get("id"), (String) m.get("name"), (String) m.get("type"),
                    num(m.get("lat")), num(m.get("lon")), (String) m.get("taluk"), (int) num(m.get("x")), (int) num(m.get("y"))));
        }
        for (Object o : (List<Object>) root.get("roads")) {
            Map<String, Object> m = (Map<String, Object>) o;
            d.roads.add(new Road((String) m.get("from"), (String) m.get("to"), num(m.get("km")), num(m.get("traffic")),
                    Boolean.TRUE.equals(m.get("blocked"))));
        }
        for (Object o : (List<Object>) root.get("units")) {
            Map<String, Object> m = (Map<String, Object>) o;
            d.units.add(ResponseUnit.create((String) m.get("id"), (String) m.get("type"), (String) m.get("station")));
        }
        for (Object o : (List<Object>) root.get("incidents")) {
            Map<String, Object> m = (Map<String, Object>) o;
            Set<String> needs = new LinkedHashSet<>();
            for (Object n : (List<Object>) m.get("needs")) needs.add((String) n);
            d.incidents.add(new Incident((String) m.get("id"), (String) m.get("type"), (String) m.get("location"),
                    (int) num(m.get("severity")), num(m.get("reportedAt")), needs));
        }
        d.linkIncidents();
        return d;
    }
    private static double num(Object o) { return ((Number) o).doubleValue(); }
}
