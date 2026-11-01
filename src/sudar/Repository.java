package sudar;

/** Where the data lives. Two implementations: JsonRepository (file) and JdbcRepository (MySQL). */
public interface Repository {
    String name();
    /** Reads locations, roads, units and incidents (results are recomputed by DispatchEngine). */
    Dataset load() throws Exception;
    /** Writes incidents, road states, assignments and the dispatch log back (no-op for the JSON file). */
    void saveAll(Dataset data) throws Exception;
    boolean persistent();
}
