package sudar;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * MySQL storage through plain JDBC (java.sql). Needs the MySQL Connector/J jar on the
 * class path at run time (see README). Tables are defined in database/schema.sql.
 */
public class JdbcRepository implements Repository {
    private final String host, port, dbName, user, password;

    public JdbcRepository(Properties cfg) {
        host = cfg.getProperty("db.host", "localhost");
        port = cfg.getProperty("db.port", "3306");
        dbName = cfg.getProperty("db.name", "sudar_dispatch");
        user = cfg.getProperty("db.user", "root");
        password = cfg.getProperty("db.password", "");
    }

    private static final String OPTS = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
    private Connection open(boolean withDb) throws SQLException {
        String url = "jdbc:mysql://" + host + ":" + port + "/" + (withDb ? dbName : "") + OPTS;
        return DriverManager.getConnection(url, user, password);
    }

    @Override public String name() { return "MySQL (" + host + ":" + port + "/" + dbName + ")"; }
    @Override public boolean persistent() { return true; }

    /** True when the database answers and contains data. */
    public boolean hasData() {
        try (Connection c = open(true); Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM locations")) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) { return false; }
    }
    public void ping() throws SQLException { try (Connection c = open(false)) { /* connected */ } }

    /** Creates the database and tables by running database/schema.sql. */
    public void createSchema(Path schemaFile) throws SQLException, IOException {
        String sql = new String(Files.readAllBytes(schemaFile), StandardCharsets.UTF_8);
        StringBuilder clean = new StringBuilder();
        for (String line : sql.split("\n")) if (!line.trim().startsWith("--")) clean.append(line).append('\n');
        try (Connection c = open(false); Statement st = c.createStatement()) {
            for (String stmt : clean.toString().split(";")) if (!stmt.trim().isEmpty()) st.execute(stmt);
        }
    }

    @Override public Dataset load() throws Exception {
        Dataset d = new Dataset();
        try (Connection c = open(true); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT id,name,type,lat,lon,taluk,x,y FROM locations ORDER BY seq")) {
                while (rs.next()) d.locations.put(rs.getString(1), new Location(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getDouble(4), rs.getDouble(5), rs.getString(6), rs.getInt(7), rs.getInt(8)));
            }
            try (ResultSet rs = st.executeQuery("SELECT from_id,to_id,km,traffic,blocked FROM roads ORDER BY id")) {
                while (rs.next()) d.roads.add(new Road(rs.getString(1), rs.getString(2), rs.getDouble(3), rs.getDouble(4), rs.getBoolean(5)));
            }
            try (ResultSet rs = st.executeQuery("SELECT id,type,station FROM units ORDER BY seq")) {
                while (rs.next()) d.units.add(ResponseUnit.create(rs.getString(1), rs.getString(2), rs.getString(3)));
            }
            Map<String, Set<String>> needs = new HashMap<>();
            try (ResultSet rs = st.executeQuery("SELECT incident_id,need FROM incident_needs ORDER BY incident_id,seq")) {
                while (rs.next()) needs.computeIfAbsent(rs.getString(1), k -> new LinkedHashSet<>()).add(rs.getString(2));
            }
            try (ResultSet rs = st.executeQuery("SELECT id,type,location,severity,reported_at FROM incidents ORDER BY reported_at,id")) {
                while (rs.next()) d.incidents.add(new Incident(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                        rs.getDouble(5), needs.getOrDefault(rs.getString(1), new LinkedHashSet<>())));
            }
        }
        d.linkIncidents();
        return d;
    }

    /** Replaces the whole database content with {@code d} (used for the first import and after every dispatch run). */
    @Override public void saveAll(Dataset d) throws Exception {
        try (Connection c = open(true)) {
            c.setAutoCommit(false);
            try {
                try (Statement st = c.createStatement()) {
                    st.execute("SET FOREIGN_KEY_CHECKS=0");
                    for (String t : new String[]{"assignments", "dispatch_log", "incident_needs", "incidents", "roads", "units", "locations"})
                        st.execute("DELETE FROM " + t);
                    st.execute("SET FOREIGN_KEY_CHECKS=1");
                }
                int seq = 0;
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO locations(seq,id,name,type,lat,lon,taluk,x,y) VALUES (?,?,?,?,?,?,?,?,?)")) {
                    for (Location l : d.locations.values()) {
                        ps.setInt(1, seq++); ps.setString(2, l.getId()); ps.setString(3, l.getName()); ps.setString(4, l.getType());
                        ps.setDouble(5, l.getLat()); ps.setDouble(6, l.getLon()); ps.setString(7, l.getTaluk()); ps.setInt(8, l.getX()); ps.setInt(9, l.getY());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO roads(from_id,to_id,km,traffic,blocked) VALUES (?,?,?,?,?)")) {
                    for (Road r : d.roads) {
                        ps.setString(1, r.getFrom()); ps.setString(2, r.getTo()); ps.setDouble(3, r.getKm()); ps.setDouble(4, r.getTraffic());
                        ps.setBoolean(5, r.isBlocked()); ps.addBatch();
                    }
                    ps.executeBatch();
                }
                seq = 0;
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO units(seq,id,type,station) VALUES (?,?,?,?)")) {
                    for (ResponseUnit u : d.units) {
                        ps.setInt(1, seq++); ps.setString(2, u.getId()); ps.setString(3, u.getType().name()); ps.setString(4, u.getStation()); ps.addBatch();
                    }
                    ps.executeBatch();
                }
                try (PreparedStatement pi = c.prepareStatement("INSERT INTO incidents(id,type,location,severity,reported_at,status,response_minutes) VALUES (?,?,?,?,?,?,?)");
                     PreparedStatement pn = c.prepareStatement("INSERT INTO incident_needs(incident_id,seq,need) VALUES (?,?,?)")) {
                    for (Incident i : d.incidents) {
                        pi.setString(1, i.getId()); pi.setString(2, i.getType()); pi.setString(3, i.getLocation()); pi.setInt(4, i.getSeverity());
                        pi.setDouble(5, i.getReportedAt()); pi.setString(6, i.getStatus().name());
                        if (i.getResponseMinutes() == null) pi.setNull(7, Types.DOUBLE); else pi.setDouble(7, i.getResponseMinutes());
                        pi.addBatch();
                        int n = 0;
                        for (String need : i.getNeeds()) { pn.setString(1, i.getId()); pn.setInt(2, n++); pn.setString(3, need); pn.addBatch(); }
                    }
                    pi.executeBatch(); pn.executeBatch();
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO assignments(incident_id,unit_id,path,km,minutes,dispatched_at,arrival_at) VALUES (?,?,?,?,?,?,?)")) {
                    for (Assignment a : d.assignments) {
                        ps.setString(1, a.getIncidentId()); ps.setString(2, a.getUnitId()); ps.setString(3, String.join(">", a.getPath()));
                        ps.setDouble(4, a.getKm()); ps.setDouble(5, a.getMinutes()); ps.setDouble(6, a.getDispatchedAt()); ps.setDouble(7, a.getArrivalAt());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO dispatch_log(line_no,message) VALUES (?,?)")) {
                    int n = 0; for (String line : d.log) { ps.setInt(1, n++); ps.setString(2, line); ps.addBatch(); }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; }
        }
    }
}
