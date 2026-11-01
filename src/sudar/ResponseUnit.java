package sudar;

import java.util.HashSet;
import java.util.Set;

/**
 * Abstract base class of every emergency unit (abstraction + inheritance).
 * Each subclass declares the equipment it carries; the equipment is kept in a
 * HashSet so "can this unit handle that need?" is a constant-time check.
 */
public abstract class ResponseUnit {
    private final String id, station;          // station = id of the Location it is based at
    protected final Set<String> equipment = new HashSet<>();
    private double busyUntil = 0;              // simulation minute at which the unit becomes free

    protected ResponseUnit(String id, String station) { this.id = id; this.station = station; }

    public abstract UnitType getType();                      // polymorphic
    public abstract String describe();                       // polymorphic

    /** A unit can handle a need (e.g. "FIRE") when its equipment set contains it. */
    public boolean canHandle(String need) { return equipment.contains(need); }
    public boolean canHandleAll(Set<String> needs) { return equipment.containsAll(needs); }

    public String getId() { return id; }
    public String getStation() { return station; }
    public Set<String> getEquipment() { return equipment; }
    public double getBusyUntil() { return busyUntil; }
    public void setBusyUntil(double t) { this.busyUntil = t; }
    public void reset() { busyUntil = 0; }

    /** Factory: builds the right subclass from the type stored in the data set / database. */
    public static ResponseUnit create(String id, String type, String station) {
        switch (UnitType.valueOf(type)) {
            case FIRE:      return new FireEngine(id, station);
            case POLICE:    return new PoliceVehicle(id, station);
            default:        return new Ambulance(id, station);
        }
    }

    @Override public String toString() { return id + " [" + getType() + "] @" + station; }
}
