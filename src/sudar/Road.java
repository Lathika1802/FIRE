package sudar;

/** A two-way road between two locations (graph edge). */
public class Road {
    public static final double SPEED_KMH = 80;      // same speed as the web dispatch engine

    private final String from, to;
    private final double km, traffic;
    private boolean blocked;

    public Road(String from, String to, double km, double traffic, boolean blocked) {
        this.from = from; this.to = to; this.km = km; this.traffic = traffic; this.blocked = blocked;
    }

    public String getFrom() { return from; }
    public String getTo() { return to; }
    public double getKm() { return km; }
    public double getTraffic() { return traffic; }
    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }

    /** Travel time in minutes = km x traffic multiplier at SPEED_KMH. */
    public double travelMinutes() { return km * traffic * 60.0 / SPEED_KMH; }

    @Override public String toString() { return from + " <-> " + to + "  " + km + " km  x" + traffic + (blocked ? "  [CLOSED]" : ""); }
}
