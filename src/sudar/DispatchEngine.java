package sudar;

import java.util.*;

/**
 * Dispatch logic. Incidents wait in a PriorityQueue ordered by Incident.compareTo
 * (the four-tier rule). For every need of an incident (FIRE, POLICE, AMBULANCE) the
 * engine checks the unit's equipment HashSet, runs Dijkstra from the incident to the
 * unit's station over open roads, and picks the unit that can arrive earliest
 * (a busy unit starts only when it is free again).
 */
public class DispatchEngine {
    public static final double ON_SCENE_MIN = 20;          // a unit stays busy this long after arriving

    private final Dataset data;
    public DispatchEngine(Dataset data) { this.data = data; }

    private static String f1(double v) { return String.format("%5.1f", v); }

    /** Replays every incident from time zero and rebuilds assignments, statuses and the log. */
    public void run() {
        data.linkIncidents();
        RoadGraph graph = new RoadGraph(data.locations.values(), data.roads);
        Map<String, RoadGraph.ShortestPaths> cache = new HashMap<>();       // one Dijkstra per incident location
        for (ResponseUnit u : data.units) u.reset();
        data.assignments.clear(); data.log.clear();
        for (Incident i : data.incidents) { i.setStatus(Incident.Status.PENDING); i.setResponseMinutes(null); }

        // arrivals: incidents ordered by report time; pending: PriorityQueue by priority
        List<Incident> arrivals = new ArrayList<>(data.incidents);
        arrivals.sort(Comparator.comparingDouble(Incident::getReportedAt).thenComparing(Incident::getId));
        PriorityQueue<Incident> pending = new PriorityQueue<>();
        int next = 0;
        List<Double> responseTimes = new ArrayList<>();

        while (next < arrivals.size() || !pending.isEmpty()) {
            if (pending.isEmpty()) {                                        // advance the clock to the next report
                double t = arrivals.get(next).getReportedAt();
                while (next < arrivals.size() && arrivals.get(next).getReportedAt() <= t) pending.add(arrivals.get(next++));
            }
            Incident inc = pending.poll();                                  // most urgent incident first
            Double rt = dispatch(inc, graph, cache);
            if (rt != null) responseTimes.add(rt);
        }
        double sum = 0; for (double d : responseTimes) sum += d;
        data.averageResponseMinutes = responseTimes.isEmpty() ? 0 : sum / responseTimes.size();
    }

    private Double dispatch(Incident inc, RoadGraph graph, Map<String, RoadGraph.ShortestPaths> cache) {
        data.log.add("t=" + f1(inc.getReportedAt()) + "  REPORT   " + inc.getId() + " " + inc.getType()
                + " at " + inc.getLocation() + " (severity " + inc.getSeverity() + ")");
        RoadGraph.ShortestPaths sp = cache.computeIfAbsent(inc.getLocation(), graph::dijkstra);
        List<Double> arrivals = new ArrayList<>();
        boolean delayed = false; int missing = 0;

        for (String need : inc.getNeeds()) {
            ResponseUnit best = null; double bestStart = 0, bestArr = 0, bestDist = 0;
            for (ResponseUnit u : data.units) {
                if (!u.canHandle(need)) continue;                           // HashSet equipment check
                double d = sp.minutesTo(u.getStation());
                if (Double.isInfinite(d)) continue;                         // blocked / unreachable
                double start = Math.max(inc.getReportedAt(), u.getBusyUntil());
                double arr = start + d;
                if (best == null || arr < bestArr) { best = u; bestStart = start; bestArr = arr; bestDist = d; }
            }
            if (best == null) {
                missing++;
                data.log.add("t=" + f1(inc.getReportedAt()) + "  NO UNIT  " + need + " unavailable/unreachable for " + inc.getId());
                continue;
            }
            List<String> path = new ArrayList<>(sp.pathTo(best.getStation()));
            Collections.reverse(path);                                      // station -> incident
            best.setBusyUntil(bestArr + ON_SCENE_MIN);
            if (bestStart > inc.getReportedAt()) delayed = true;
            arrivals.add(bestArr);
            data.assignments.add(new Assignment(inc.getId(), best.getId(), path, sp.kmTo(best.getStation()),
                    bestDist, bestStart, bestArr));
            data.log.add("t=" + f1(bestStart) + "  DISPATCH " + best.getId() + " -> " + inc.getId()
                    + " (" + (path.size() - 1) + " hops, " + String.format("%.1f", bestDist) + " min)");
        }
        inc.setStatus(arrivals.isEmpty() ? Incident.Status.UNREACHABLE
                : (delayed || missing > 0) ? Incident.Status.QUEUED : Incident.Status.DISPATCHED);
        if (arrivals.isEmpty()) return null;
        double first = Collections.min(arrivals) - inc.getReportedAt();
        inc.setResponseMinutes(first);
        return first;
    }

    /** All incidents ranked by priority (does not change anything). */
    public List<Incident> ranking() {
        data.linkIncidents();
        PriorityQueue<Incident> q = new PriorityQueue<>(data.incidents);
        List<Incident> out = new ArrayList<>();
        while (!q.isEmpty()) out.add(q.poll());
        return out;
    }

    /** Adds a new incident (reported 3 minutes after the latest one), then re-dispatches everything. */
    public Incident report(String type, String location, int severity, Set<String> needs) {
        Incident inc = new Incident(data.nextIncidentId(), type, location, severity, data.lastReportTime() + 3, needs);
        data.incidents.add(inc);
        run();
        return inc;
    }

    public Optional<String> routeBetween(String from, String to, double[] outMinutesKm, List<String> outPath) {
        RoadGraph g = new RoadGraph(data.locations.values(), data.roads);
        RoadGraph.ShortestPaths sp = g.dijkstra(from);
        if (!sp.reachable(to)) return Optional.empty();
        outPath.addAll(sp.pathTo(to));
        outMinutesKm[0] = sp.minutesTo(to); outMinutesKm[1] = sp.kmTo(to);
        return Optional.of("ok");
    }
}
