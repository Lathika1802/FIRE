# Java program (src/sudar)

Menu-driven console program plus a small REST API. Start it with `run.bat` (Windows) or `./run.sh` (Linux / macOS)
from the `sudar-dispatch` folder. Needs JDK 11 or newer. See the project README for details.

| File | Purpose |
|------|---------|
| `Main.java` | Console menu (10 options) |
| `Location.java`, `Road.java` | Map data classes (encapsulation) |
| `ResponseUnit.java` (abstract), `FireEngine.java`, `PoliceVehicle.java`, `Ambulance.java` | Inheritance / polymorphism, HashSet equipment |
| `Incident.java` | `Comparable` with the 4-tier priority rule |
| `RoadGraph.java` | Adjacency-list graph + Dijkstra with `PriorityQueue` |
| `DispatchEngine.java` | Ranks incidents (PriorityQueue) and assigns units |
| `Dataset.java`, `Assignment.java` | Collections used by the engine |
| `Repository.java`, `JsonRepository.java`, `JdbcRepository.java` | Storage: JSON file or MySQL (JDBC) |
| `AppState.java`, `ApiServer.java` | Shared state and the REST API used by `web/backend.html` |
| `MiniJson.java` | Small JSON reader/writer |
