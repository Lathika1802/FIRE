package sudar;

/** Fire tender: water, hose and ladder equipment. */
public class FireEngine extends ResponseUnit {
    public FireEngine(String id, String station) {
        super(id, station);
        equipment.add("FIRE"); equipment.add("WATER_TANK"); equipment.add("HOSE"); equipment.add("LADDER");
    }
    @Override public UnitType getType() { return UnitType.FIRE; }
    @Override public String describe() { return "Fire engine with water tank, hose and ladder"; }
}
