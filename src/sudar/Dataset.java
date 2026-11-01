package sudar;

import java.util.*;

/** Everything the program works on, held in standard collections. */
public class Dataset {
    public String town = "Erode District, Tamil Nadu";
    public final Map<String, Location> locations = new LinkedHashMap<>();   // HashMap-style lookup by id, keeps file order
    public final List<Road> roads = new ArrayList<>();
    public final List<ResponseUnit> units = new ArrayList<>();
    public final List<Incident> incidents = new ArrayList<>();
    public final List<Assignment> assignments = new ArrayList<>();
    public final List<String> log = new ArrayList<>();
    public double averageResponseMinutes = 0;

    public Location location(String id) { return locations.get(id); }
    public String placeName(String id) { Location l = locations.get(id); return l == null ? id : l.getName(); }

    /** Copies ranking context (critical infrastructure / population exposure) into every incident. */
    public void linkIncidents() {
        for (Incident i : incidents) { Location l = locations.get(i.getLocation()); if (l != null) i.applyLocation(l); }
    }

    public Road findRoad(String a, String b) {
        for (Road r : roads)
            if ((r.getFrom().equals(a) && r.getTo().equals(b)) || (r.getFrom().equals(b) && r.getTo().equals(a))) return r;
        return null;
    }

    public String nextIncidentId() { return String.format("INC-%02d", incidents.size() + 1); }
    public double lastReportTime() { double m = 0; for (Incident i : incidents) m = Math.max(m, i.getReportedAt()); return m; }

    /** Same JSON shape as data/sudar-data.json (plus results), used by the HTTP API. */
    public Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("town", town);
        List<Object> ls = new ArrayList<>();
        for (Location l : locations.values()) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", l.getId()); o.put("name", l.getName()); o.put("type", l.getType());
            o.put("lat", l.getLat()); o.put("lon", l.getLon()); o.put("taluk", l.getTaluk());
            o.put("x", (double) l.getX()); o.put("y", (double) l.getY()); ls.add(o);
        }
        m.put("locations", ls);
        List<Object> rs = new ArrayList<>();
        for (Road r : roads) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("from", r.getFrom()); o.put("to", r.getTo()); o.put("km", r.getKm());
            o.put("traffic", r.getTraffic()); o.put("blocked", r.isBlocked()); rs.add(o);
        }
        m.put("roads", rs);
        List<Object> us = new ArrayList<>();
        for (ResponseUnit u : units) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", u.getId()); o.put("type", u.getType().name()); o.put("station", u.getStation()); us.add(o);
        }
        m.put("units", us);
        List<Object> is = new ArrayList<>();
        for (Incident i : incidents) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", i.getId()); o.put("type", i.getType()); o.put("location", i.getLocation());
            o.put("severity", (double) i.getSeverity()); o.put("reportedAt", i.getReportedAt());
            o.put("needs", new ArrayList<Object>(i.getNeeds()));
            o.put("status", i.getStatus().name()); o.put("responseMinutes", i.getResponseMinutes());
            o.put("criticalInfrastructure", i.isCriticalInfrastructure());
            o.put("populationExposure", (double) i.getPopulationExposure());
            is.add(o);
        }
        m.put("incidents", is);
        List<Object> as = new ArrayList<>();
        for (Assignment a : assignments) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("incident", a.getIncidentId()); o.put("unit", a.getUnitId());
            o.put("path", new ArrayList<Object>(a.getPath()));
            o.put("km", a.getKm()); o.put("minutes", a.getMinutes());
            o.put("dispatchedAt", a.getDispatchedAt()); o.put("arrivalAt", a.getArrivalAt()); as.add(o);
        }
        m.put("assignments", as);
        m.put("log", new ArrayList<Object>(log));
        m.put("averageResponseMinutes", averageResponseMinutes);
        return m;
    }
}
