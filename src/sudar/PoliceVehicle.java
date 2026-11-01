package sudar;

/** Police patrol vehicle: traffic control and law-and-order equipment. */
public class PoliceVehicle extends ResponseUnit {
    public PoliceVehicle(String id, String station) {
        super(id, station);
        equipment.add("POLICE"); equipment.add("BARRICADES"); equipment.add("RADIO");
    }
    @Override public UnitType getType() { return UnitType.POLICE; }
    @Override public String describe() { return "Police vehicle with barricades and radio"; }
}
