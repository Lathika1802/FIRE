package sudar;

import java.util.*;

/**
 * Weighted, undirected road network stored as an adjacency list
 * (HashMap of location id -> list of edges). Blocked roads are never added,
 * so routing automatically avoids them. Shortest paths use Dijkstra's
 * algorithm with a PriorityQueue (binary min-heap): O((V + E) log V).
 */
public class RoadGraph {
    private static final class Edge {
        final String to; final double minutes, km;
        Edge(String to, double minutes, double km) { this.to = to; this.minutes = minutes; this.km = km; }
    }
    private static final class Node implements Comparable<Node> {
        final String id; final double dist;
        Node(String id, double dist) { this.id = id; this.dist = dist; }
        public int compareTo(Node o) { return Double.compare(dist, o.dist); }
    }

    /** Result of one Dijkstra run from a single source. */
    public static final class ShortestPaths {
        private final String source;
        private final Map<String, Double> dist, km;
        private final Map<String, String> prev;
        ShortestPaths(String source, Map<String, Double> dist, Map<String, Double> km, Map<String, String> prev) {
            this.source = source; this.dist = dist; this.km = km; this.prev = prev;
        }
        public double minutesTo(String id) { return dist.getOrDefault(id, Double.POSITIVE_INFINITY); }
        public double kmTo(String id) { return km.getOrDefault(id, Double.POSITIVE_INFINITY); }
        public boolean reachable(String id) { return !Double.isInfinite(minutesTo(id)); }
        /** Route from the Dijkstra source to {@code id}, or null when unreachable. */
        public List<String> pathTo(String id) {
            if (!reachable(id)) return null;
            LinkedList<String> p = new LinkedList<>();
            for (String c = id; c != null; c = prev.get(c)) p.addFirst(c);
            return p;
        }
        public String getSource() { return source; }
    }

    private final Map<String, List<Edge>> adj = new HashMap<>();

    public RoadGraph(Collection<Location> locations, Collection<Road> roads) {
        for (Location l : locations) adj.put(l.getId(), new ArrayList<>());
        for (Road r : roads) {
            if (r.isBlocked() || !adj.containsKey(r.getFrom()) || !adj.containsKey(r.getTo())) continue;
            double w = r.travelMinutes();
            adj.get(r.getFrom()).add(new Edge(r.getTo(), w, r.getKm()));
            adj.get(r.getTo()).add(new Edge(r.getFrom(), w, r.getKm()));
        }
    }

    public int nodeCount() { return adj.size(); }
    public int edgeCount() { int n = 0; for (List<Edge> e : adj.values()) n += e.size(); return n / 2; }

    /** Dijkstra from {@code source} to every reachable location. */
    public ShortestPaths dijkstra(String source) {
        Map<String, Double> dist = new HashMap<>(), km = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        for (String id : adj.keySet()) dist.put(id, Double.POSITIVE_INFINITY);
        PriorityQueue<Node> heap = new PriorityQueue<>();
        Set<String> done = new HashSet<>();
        if (!adj.containsKey(source)) return new ShortestPaths(source, dist, km, prev);
        dist.put(source, 0.0); km.put(source, 0.0);
        heap.add(new Node(source, 0));
        while (!heap.isEmpty()) {
            Node cur = heap.poll();
            if (!done.add(cur.id)) continue;               // stale heap entry
            for (Edge e : adj.get(cur.id)) {
                double nd = cur.dist + e.minutes;
                if (nd < dist.get(e.to)) {
                    dist.put(e.to, nd);
                    km.put(e.to, km.get(cur.id) + e.km);
                    prev.put(e.to, cur.id);
                    heap.add(new Node(e.to, nd));
                }
            }
        }
        return new ShortestPaths(source, dist, km, prev);
    }
}
