package sudar;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Menu-driven console program: Emergency Response Management System, Erode District.
 * Shows Java OOP (abstract class, inheritance, polymorphism, interface, encapsulation),
 * data structures (PriorityQueue, HashMap, HashSet, ArrayList, LinkedList, graph as adjacency list),
 * Dijkstra's algorithm and MySQL storage through JDBC.
 */
public class Main {
    private static Path HOME = Paths.get(".");
    private static Properties cfg = new Properties();
    private static AppState app;
    private static BufferedReader in;
    private static ApiServer api;

    public static void main(String[] args) throws Exception {
        HOME = Paths.get(System.getProperty("sudar.home", "."));
        loadConfig();
        in = new BufferedReader(new InputStreamReader(System.in));
        Path json = HOME.resolve("data").resolve("sudar-data.json");
        if (!Files.exists(json)) { System.out.println("Cannot find " + json.toAbsolutePath() + " - run from the sudar-dispatch folder."); return; }

        // Start with MySQL when it is configured and already holds data; otherwise use the JSON file.
        Repository start = new JsonRepository(json);
        if (cfg.getProperty("db.enabled", "false").equalsIgnoreCase("true")) {
            try {
                JdbcRepository jr = new JdbcRepository(cfg);
                if (jr.hasData()) start = jr; else System.out.println("MySQL is enabled but empty -> use menu 9 to set it up. Starting with the JSON file.");
            } catch (Throwable t) { System.out.println("MySQL not available (" + t.getMessage() + "). Starting with the JSON file."); }
        }
        app = new AppState(start);

        if (args.length > 0 && args[0].equals("--api")) {       // headless: just run the web API
            int port = args.length > 1 ? Integer.parseInt(args[1]) : Integer.parseInt(cfg.getProperty("api.port", "8080"));
            startApi(port); Thread.currentThread().join(); return;
        }
        menu();
        if (api != null) api.stop();
    }

    private static void loadConfig() {
        Path f = HOME.resolve("config.properties");
        if (Files.exists(f)) try (InputStream is = Files.newInputStream(f)) { cfg.load(is); } catch (IOException ignored) { }
    }

    // ------------------------------------------------------------------ input helpers
    private static String ask(String prompt) {
        System.out.print(prompt);
        try { String s = in.readLine(); if (s == null) { System.out.println(); System.exit(0); } return s.trim(); }
        catch (IOException e) { return ""; }
    }
    private static int askInt(String prompt, int min, int max, int dflt) {
        while (true) {
            String s = ask(prompt);
            if (s.isEmpty() && dflt >= min) return dflt;
            try { int v = Integer.parseInt(s); if (v >= min && v <= max) return v; } catch (NumberFormatException ignored) { }
            System.out.println("  Please enter a number from " + min + " to " + max + ".");
        }
    }
    private static String f1(double v) { return String.format("%.1f", v); }

    /** Accepts a location id (ERD) or part of a name and lets the user pick when several match. */
    private static Location pickLocation(String prompt) {
        Dataset d = app.data();
        while (true) {
            String q = ask(prompt);
            if (q.isEmpty()) return null;
            Location exact = d.location(q.toUpperCase());
            if (exact != null) return exact;
            List<Location> hits = new ArrayList<>();
            for (Location l : d.locations.values()) if (l.getName().toLowerCase().contains(q.toLowerCase())) hits.add(l);
            if (hits.isEmpty()) { System.out.println("  No place matches '" + q + "'."); continue; }
            if (hits.size() == 1) return hits.get(0);
            for (int i = 0; i < Math.min(hits.size(), 15); i++) System.out.printf("  %2d) %s%n", i + 1, hits.get(i));
            if (hits.size() > 15) System.out.println("  ... " + (hits.size() - 15) + " more - type a longer name.");
            int n = askInt("  Choose number (0 = search again): ", 0, Math.min(hits.size(), 15), 0);
            if (n > 0) return hits.get(n - 1);
        }
    }

    // ------------------------------------------------------------------ main menu
    private static void menu() {
        while (true) {
            System.out.println();
            System.out.println("==== EMERGENCY RESPONSE MANAGEMENT SYSTEM - ERODE DISTRICT (Java console) ====");
            System.out.println("Data source : " + app.repo().name());
            System.out.println(" 1. View places");
            System.out.println(" 2. View response units");
            System.out.println(" 3. View roads / close or reopen a road");
            System.out.println(" 4. Report new incident (auto-dispatch)");
            System.out.println(" 5. Incident priority queue (4-tier ranking)");
            System.out.println(" 6. Assignments and dispatch log");
            System.out.println(" 7. Shortest route between two places (Dijkstra)");
            System.out.println(" 8. OOP and data-structure demonstration");
            System.out.println(" 9. MySQL database menu");
            System.out.println("10. Start / stop web API (for the web 'Java + MySQL' page)");
            System.out.println(" 0. Exit");
            switch (askInt("Choose option: ", 0, 10, -1)) {
                case 1: viewPlaces(); break;
                case 2: viewUnits(); break;
                case 3: roadsMenu(); break;
                case 4: reportIncident(); break;
                case 5: showRanking(); break;
                case 6: showAssignments(); break;
                case 7: shortestRoute(); break;
                case 8: demo(); break;
                case 9: mysqlMenu(); break;
                case 10: toggleApi(); break;
                default: System.out.println("Goodbye."); return;
            }
        }
    }

    private static void viewPlaces() {
        Dataset d = app.data();
        String taluk = ask("Taluk to show (blank = all " + d.locations.size() + " places): ").toLowerCase();
        int n = 0;
        System.out.printf("%-6s %-44s %-12s %s%n", "ID", "Place", "Type", "Taluk");
        for (Location l : d.locations.values()) {
            if (!taluk.isEmpty() && !l.getTaluk().toLowerCase().contains(taluk)) continue;
            System.out.printf("%-6s %-44s %-12s %s%n", l.getId(), l.getName(), l.getType(), l.getTaluk()); n++;
        }
        System.out.println(n + " place(s).");
    }

    private static void viewUnits() {
        Dataset d = app.data();
        Map<UnitType, Integer> count = new EnumMap<>(UnitType.class);
        System.out.printf("%-14s %-10s %-34s %s%n", "Unit", "Type", "Station", "Equipment");
        for (ResponseUnit u : d.units) {
            count.merge(u.getType(), 1, Integer::sum);
            System.out.printf("%-14s %-10s %-34s %s%n", u.getId(), u.getType(), d.placeName(u.getStation()), new TreeSet<>(u.getEquipment()));
        }
        System.out.println("Fleet: " + count);
    }

    private static void roadsMenu() {
        Dataset d = app.data();
        long closed = d.roads.stream().filter(Road::isBlocked).count();
        System.out.println(d.roads.size() + " roads, " + closed + " closed.");
        System.out.println("  1) list closed roads   2) list all roads   3) close a road   4) reopen a road   0) back");
        int c = askInt("Choose: ", 0, 4, 0);
        if (c == 1 || c == 2) {
            for (Road r : d.roads) if (c == 2 || r.isBlocked())
                System.out.printf("  %-6s <-> %-6s %6.1f km  x%.1f  %s   (%s <-> %s)%n", r.getFrom(), r.getTo(), r.getKm(), r.getTraffic(),
                        r.isBlocked() ? "CLOSED" : "open  ", d.placeName(r.getFrom()), d.placeName(r.getTo()));
        } else if (c == 3 || c == 4) {
            Location a = pickLocation("  From place (id or name): "); if (a == null) return;
            Location b = pickLocation("  To place   (id or name): "); if (b == null) return;
            if (!app.setRoad(a.getId(), b.getId(), c == 3)) { System.out.println("  There is no direct road between those places."); return; }
            System.out.println("  Road " + a.getId() + " <-> " + b.getId() + (c == 3 ? " closed" : " reopened") + ". All incidents re-dispatched.");
            printSummary(); saveMsg();
        }
    }

    private static void reportIncident() {
        Dataset d = app.data();
        String type = ask("Incident type (e.g. Warehouse Fire): ");
        if (type.isEmpty()) { System.out.println("Cancelled."); return; }
        Location loc = pickLocation("Location (id or part of name): ");
        if (loc == null) { System.out.println("Cancelled."); return; }
        int sev = askInt("Severity 1-5 [3]: ", 1, 5, 3);
        Set<String> needs = new LinkedHashSet<>();
        while (needs.isEmpty()) {
            for (String n : ask("Units needed, comma separated (FIRE, POLICE, AMBULANCE) [POLICE]: ").toUpperCase().split(",")) {
                n = n.trim(); if (n.isEmpty()) continue;
                try { needs.add(UnitType.valueOf(n).name()); } catch (IllegalArgumentException e) { System.out.println("  Unknown unit type: " + n); }
            }
            if (needs.isEmpty() && ask("  Use POLICE? (y/n): ").toLowerCase().startsWith("y")) needs.add("POLICE");
        }
        Incident inc = app.report(type, loc.getId(), sev, needs);
        System.out.println();
        System.out.println(inc.getId() + " reported at " + loc.getName() + "  [critical infrastructure: " + (inc.isCriticalInfrastructure() ? "yes" : "no")
                + ", population exposure " + inc.getPopulationExposure() + "]");
        System.out.println("Status: " + inc.getStatus() + (inc.getResponseMinutes() != null ? "  - first unit arrives in " + f1(inc.getResponseMinutes()) + " min" : "  - no reachable unit"));
        for (Assignment a : d.assignments) if (a.getIncidentId().equals(inc.getId())) printAssignment(a);
        saveMsg();
    }

    private static void printAssignment(Assignment a) {
        Dataset d = app.data();
        StringBuilder route = new StringBuilder();
        for (String id : a.getPath()) { if (route.length() > 0) route.append(" -> "); route.append(d.placeName(id)); }
        System.out.printf("  %s  %-14s %5.1f km  %5.1f min  dispatched t+%.1f, arrives t+%.1f%n      route: %s%n",
                a.getIncidentId(), a.getUnitId(), a.getKm(), a.getMinutes(), a.getDispatchedAt(), a.getArrivalAt(), route);
    }

    private static void showRanking() {
        System.out.println("Incidents in PriorityQueue order (tier 1 critical infrastructure, 2 severity, 3 population exposure, 4 earlier report):");
        System.out.printf("%-3s %-8s %-26s %-30s %-4s %-5s %-4s %-8s %s%n", "#", "ID", "Type", "Location", "Crit", "Sev", "Pop", "Reported", "Status");
        int n = 1;
        for (Incident i : app.engine().ranking())
            System.out.printf("%-3d %-8s %-26s %-30s %-4s %-5d %-4d t=%-6s %s%n", n++, i.getId(), cut(i.getType(), 26), cut(app.data().placeName(i.getLocation()), 30),
                    i.isCriticalInfrastructure() ? "yes" : "no", i.getSeverity(), i.getPopulationExposure(), f1(i.getReportedAt()), i.getStatus());
    }
    private static String cut(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "~"; }

    private static void showAssignments() {
        Dataset d = app.data();
        printSummary();
        System.out.println("\nAssignments:");
        for (Assignment a : d.assignments) printAssignment(a);
        System.out.println("\nDispatch log:");
        for (String line : d.log) System.out.println("  " + line);
    }

    private static void printSummary() {
        Dataset d = app.data();
        Map<Incident.Status, Integer> st = new EnumMap<>(Incident.Status.class);
        for (Incident i : d.incidents) st.merge(i.getStatus(), 1, Integer::sum);
        System.out.println("Incidents: " + d.incidents.size() + " " + st + "   average response time: " + f1(d.averageResponseMinutes) + " min");
    }

    private static void shortestRoute() {
        Location a = pickLocation("From place (id or name): "); if (a == null) return;
        Location b = pickLocation("To place   (id or name): "); if (b == null) return;
        double[] mk = new double[2]; List<String> path = new ArrayList<>();
        if (!app.engine().routeBetween(a.getId(), b.getId(), mk, path).isPresent()) { System.out.println("No route - the destination is unreachable over open roads."); return; }
        System.out.println(f1(mk[0]) + " min, " + f1(mk[1]) + " km, " + (path.size() - 1) + " hops:");
        for (int i = 0; i < path.size(); i++) System.out.println("  " + (i + 1) + ". " + app.data().placeName(path.get(i)));
    }

    // ------------------------------------------------------------------ OOP + data structures demo
    private static void demo() {
        Dataset d = app.data();
        System.out.println("\n--- OOP concepts ---");
        System.out.println("Abstraction / inheritance : ResponseUnit (abstract) -> FireEngine, PoliceVehicle, Ambulance");
        System.out.println("Polymorphism              : the same call unit.describe() on different objects:");
        Set<UnitType> seen = EnumSet.noneOf(UnitType.class);
        for (ResponseUnit u : d.units) if (seen.add(u.getType())) System.out.println("   " + u.getId() + " -> " + u.describe());
        System.out.println("Encapsulation             : Road, Incident, ResponseUnit keep fields private and expose getters/setters");
        System.out.println("Comparable                : Incident.compareTo() defines the 4-tier priority order");
        System.out.println("Factory method            : ResponseUnit.create(id, type, station) builds the right subclass");

        System.out.println("\n--- Data structures ---");
        System.out.println("HashMap     : Dataset.locations (id -> Location) -> " + d.locations.size() + " entries, lookup by id is O(1): ERD = " + d.placeName("ERD"));
        System.out.println("HashSet     : unit equipment, e.g. " + d.units.get(0).getId() + " " + new TreeSet<>(d.units.get(0).getEquipment())
                + " -> canHandle(\"FIRE\") = " + d.units.get(0).canHandle("FIRE") + ", canHandle(\"AMBULANCE\") = " + d.units.get(0).canHandle("AMBULANCE"));
        System.out.println("ArrayList   : roads, units, incidents, assignments, dispatch log");
        System.out.println("LinkedList  : route paths are built with addFirst() while walking back from the destination");
        RoadGraph g = new RoadGraph(d.locations.values(), d.roads);
        System.out.println("Graph       : adjacency list (HashMap<String, List<Edge>>) with " + g.nodeCount() + " nodes and " + g.edgeCount() + " open roads");
        System.out.println("PriorityQueue (min-heap) : used for (1) ranking incidents, (2) Dijkstra's frontier");
        System.out.println("  Live ranking of the current incidents:");
        int n = 1; for (Incident i : app.engine().ranking()) System.out.println("   " + n++ + ". " + i.getId() + " " + i.getType() + " (severity " + i.getSeverity() + ")");
        System.out.println("Dijkstra    : O((V + E) log V) with the heap; closed roads are never added to the graph");
        System.out.println();
    }

    // ------------------------------------------------------------------ MySQL
    private static void mysqlMenu() {
        System.out.println("\n---- MySQL database menu ----");
        System.out.println("Server: " + cfg.getProperty("db.host", "localhost") + ":" + cfg.getProperty("db.port", "3306") + "  database: "
                + cfg.getProperty("db.name", "sudar_dispatch") + "  user: " + cfg.getProperty("db.user", "root") + "   (edit config.properties to change)");
        System.out.println(" 1. Test connection");
        System.out.println(" 2. Create database and tables (runs database/schema.sql)");
        System.out.println(" 3. Import current data into MySQL (saves places, roads, units, incidents, assignments, log)");
        System.out.println(" 4. Switch to MySQL data (load from the database)");
        System.out.println(" 5. Switch back to the JSON file");
        System.out.println(" 0. Back");
        int c = askInt("Choose: ", 0, 5, 0);
        if (c == 0) return;
        try {
            JdbcRepository jr = new JdbcRepository(cfg);
            switch (c) {
                case 1: jr.ping(); System.out.println("Connected to MySQL. Tables with data: " + (jr.hasData() ? "yes" : "no (use option 2 and 3)")); break;
                case 2: jr.createSchema(HOME.resolve("database").resolve("schema.sql")); System.out.println("Database and tables are ready."); break;
                case 3: {
                    jr.saveAll(app.data()); System.out.println("Imported: " + app.data().locations.size() + " places, " + app.data().roads.size() + " roads, "
                            + app.data().units.size() + " units, " + app.data().incidents.size() + " incidents, " + app.data().assignments.size() + " assignments."); break; }
                case 4: app.use(jr); System.out.println("Now using " + app.repo().name() + " - " + app.data().incidents.size() + " incidents loaded."); break;
                default: app.use(new JsonRepository(HOME.resolve("data").resolve("sudar-data.json"))); System.out.println("Now using the JSON file (changes are not saved)."); break;
            }
        } catch (NoClassDefFoundError | Exception e) {
            String m = String.valueOf(e.getMessage());
            System.out.println("MySQL error: " + m);
            if (m.contains("No suitable driver") || e instanceof NoClassDefFoundError)
                System.out.println("  -> put mysql-connector-j-*.jar in the lib folder and start the program with run.bat / run.sh");
            else System.out.println("  -> check that MySQL is running and that config.properties has the right user and password.");
        }
    }

    private static void saveMsg() {
        String err = app.save();
        if (err != null) System.out.println("  ! " + err);
        else if (app.repo().persistent()) System.out.println("  (saved to MySQL)");
    }

    // ------------------------------------------------------------------ web API
    private static void toggleApi() {
        if (api != null) { api.stop(); api = null; System.out.println("Web API stopped."); return; }
        startApi(askInt("Port [" + cfg.getProperty("api.port", "8080") + "]: ", 1024, 65535, Integer.parseInt(cfg.getProperty("api.port", "8080"))));
    }
    private static void startApi(int port) {
        try {
            api = new ApiServer(app, port, HOME.resolve("web")); api.start();
            System.out.println("Web app running at http://localhost:" + port + "/");
            System.out.println("Open the address above to use the frontend with the Java backend. Keep this program running.");
        } catch (IOException e) { System.out.println("Could not start the API: " + e.getMessage()); api = null; }
    }
}
