package sudar;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * An emergency report. Implements Comparable so a PriorityQueue orders incidents
 * by the four-tier rule of the project report:
 *   1. critical infrastructure first      2. higher severity first
 *   3. larger population exposure first   4. earlier report time first (tie-break)
 */
public class Incident implements Comparable<Incident> {
    public enum Status { PENDING, DISPATCHED, QUEUED, UNREACHABLE }

    private final String id, type, location;
    private final int severity;
    private final double reportedAt;
    private final Set<String> needs;                 // e.g. FIRE, AMBULANCE (insertion-ordered set)
    private boolean criticalInfrastructure;
    private int populationExposure;
    private Status status = Status.PENDING;
    private Double responseMinutes;                  // null until a unit is dispatched

    public Incident(String id, String type, String location, int severity, double reportedAt, Set<String> needs) {
        this.id = id; this.type = type; this.location = location; this.severity = severity;
        this.reportedAt = reportedAt; this.needs = new LinkedHashSet<>(needs);
    }

    /** Fills the tier-1 and tier-3 ranking fields from the place where the incident happened. */
    public void applyLocation(Location loc) {
        this.criticalInfrastructure = loc.isCriticalInfrastructure();
        this.populationExposure = loc.populationExposure();
    }

    @Override public int compareTo(Incident o) {
        int c = Boolean.compare(o.criticalInfrastructure, this.criticalInfrastructure);   // tier 1
        if (c != 0) return c;
        c = Integer.compare(o.severity, this.severity);                                    // tier 2
        if (c != 0) return c;
        c = Integer.compare(o.populationExposure, this.populationExposure);                // tier 3
        if (c != 0) return c;
        c = Double.compare(this.reportedAt, o.reportedAt);                                 // tier 4
        return c != 0 ? c : this.id.compareTo(o.id);
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public String getLocation() { return location; }
    public int getSeverity() { return severity; }
    public double getReportedAt() { return reportedAt; }
    public Set<String> getNeeds() { return needs; }
    public boolean isCriticalInfrastructure() { return criticalInfrastructure; }
    public int getPopulationExposure() { return populationExposure; }
    public Status getStatus() { return status; }
    public void setStatus(Status s) { this.status = s; }
    public Double getResponseMinutes() { return responseMinutes; }
    public void setResponseMinutes(Double m) { this.responseMinutes = m; }
}
