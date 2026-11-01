package sudar;

import java.util.List;

/** One unit sent to one incident, with the route it takes. */
public class Assignment {
    private final String incidentId, unitId;
    private final List<String> path;          // location ids, station -> incident
    private final double km, minutes, dispatchedAt, arrivalAt;

    public Assignment(String incidentId, String unitId, List<String> path, double km, double minutes,
                      double dispatchedAt, double arrivalAt) {
        this.incidentId = incidentId; this.unitId = unitId; this.path = path; this.km = km;
        this.minutes = minutes; this.dispatchedAt = dispatchedAt; this.arrivalAt = arrivalAt;
    }
    public String getIncidentId() { return incidentId; }
    public String getUnitId() { return unitId; }
    public List<String> getPath() { return path; }
    public double getKm() { return km; }
    public double getMinutes() { return minutes; }
    public double getDispatchedAt() { return dispatchedAt; }
    public double getArrivalAt() { return arrivalAt; }
}
