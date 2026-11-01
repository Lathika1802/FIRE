package sudar;

/** Ambulance: stretcher and first-aid equipment. */
public class Ambulance extends ResponseUnit {
    public Ambulance(String id, String station) {
        super(id, station);
        equipment.add("AMBULANCE"); equipment.add("STRETCHER"); equipment.add("FIRST_AID");
    }
    @Override public UnitType getType() { return UnitType.AMBULANCE; }
    @Override public String describe() { return "Ambulance with stretcher and first-aid kit"; }
}
