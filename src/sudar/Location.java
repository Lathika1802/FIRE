package sudar;

/** A place on the district map (graph node). Immutable value object. */
public final class Location {
    private final String id, name, type, taluk;
    private final double lat, lon;
    private final int x, y;

    public Location(String id, String name, String type, double lat, double lon, String taluk, int x, int y) {
        this.id = id; this.name = name; this.type = type; this.lat = lat; this.lon = lon;
        this.taluk = taluk; this.x = x; this.y = y;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getTaluk() { return taluk; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public int getX() { return x; }
    public int getY() { return y; }

    /** Critical infrastructure = hospitals, stations and industrial estates (ranking tier 1). */
    public boolean isCriticalInfrastructure() {
        return type.equals("HOSPITAL") || type.equals("STATION") || type.equals("INDUSTRIAL");
    }

    /** Relative number of people exposed at this kind of place (ranking tier 3). */
    public int populationExposure() {
        switch (type) {
            case "MARKET": case "SCHOOL": return 5;
            case "HOSPITAL": case "RESIDENTIAL": return 4;
            case "INDUSTRIAL": return 3;
            default: return 2;           // STATION, JUNCTION
        }
    }

    @Override public String toString() { return id + " - " + name + " (" + type + ", " + taluk + ")"; }
}
