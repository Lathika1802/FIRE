package sudar;

import java.util.*;

/** Shared program state: the repository, the data set and the dispatch engine (used by the menu and the API server). */
public class AppState {
    private Repository repo;
    private Dataset data;
    private DispatchEngine engine;

    public AppState(Repository repo) throws Exception { use(repo); }

    /** Switches to another data source (JSON file or MySQL) and runs the dispatch engine on its data. */
    public synchronized void use(Repository r) throws Exception {
        Dataset d = r.load();
        this.repo = r; this.data = d; this.engine = new DispatchEngine(d); engine.run();
    }

    public synchronized Repository repo() { return repo; }
    public synchronized Dataset data() { return data; }
    public synchronized DispatchEngine engine() { return engine; }

    /** Saves to MySQL when the source is persistent. Returns an error message or null. */
    public synchronized String save() {
        if (!repo.persistent()) return null;
        try { repo.saveAll(data); return null; } catch (Exception e) { return "Could not save to MySQL: " + e.getMessage(); }
    }

    public synchronized Incident report(String type, String location, int severity, Set<String> needs) {
        return engine.report(type, location, severity, needs);
    }

    public synchronized boolean setRoad(String a, String b, boolean blocked) {
        Road r = data.findRoad(a, b);
        if (r == null) return false;
        r.setBlocked(blocked); engine.run();
        return true;
    }

    public synchronized Map<String, Object> snapshot() {
        Map<String, Object> m = data.toJsonMap();
        m.put("source", repo.name()); m.put("persistent", repo.persistent());
        return m;
    }
}
