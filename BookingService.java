import java.sql.*;
import java.util.concurrent.locks.ReentrantLock;

enum Strategy {
    UNSAFE("No lock (unsafe)"),
    GLOBAL_LOCK("One lock for everything"),
    PER_SEAT_LOCK("One lock per seat"),
    CAS("No Java lock (atomic SQL)");

    final String label;
    Strategy(String label) { this.label = label; }
    @Override public String toString() { return label; }
}

class BookingService {
    private final int n;
    private final String table;
    private final ReentrantLock[] locks;
    private final Object globalLock = new Object();

    BookingService(int n, String table, boolean keepData) {
        this.n = n;
        this.table = table;
        locks = new ReentrantLock[n];
        for (int i = 0; i < n; i++) locks[i] = new ReentrantLock();
        try {
            Db.createTable(table);
            if (!keepData || count("SELECT COUNT(*) FROM " + table) != n) Db.reset(table, n);
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    int size() { return n; }

    void reset() {
        try { Db.reset(table, n); } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private static void work() {
        try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    boolean book(Strategy s, int id, String user) {
        if (id < 0 || id >= n) throw new IllegalArgumentException("No such seat");
        try (Connection c = Db.get()) {
            switch (s) {
                case UNSAFE:
                    if (owner(c, id) != null) return false;
                    work();
                    return update(c, "UPDATE " + table + " SET owner=?, grant_count=grant_count+1 WHERE id=?", user, id) == 1;
                case GLOBAL_LOCK:
                    synchronized (globalLock) { return take(c, id, user); }
                case PER_SEAT_LOCK:
                    locks[id].lock();
                    try { return take(c, id, user); } finally { locks[id].unlock(); }
                case CAS:
                    if (owner(c, id) != null) return false;
                    work();
                    return update(c, "UPDATE " + table + " SET owner=?, grant_count=grant_count+1 WHERE id=? AND owner IS NULL", user, id) == 1;
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return false;
    }

    private boolean take(Connection c, int id, String user) throws SQLException {
        if (owner(c, id) != null) return false;
        work();
        return update(c, "UPDATE " + table + " SET owner=?, grant_count=grant_count+1 WHERE id=?", user, id) == 1;
    }

    boolean cancel(int id, String user) {
        if (id < 0 || id >= n) throw new IllegalArgumentException("No such seat");
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement("UPDATE " + table + " SET owner=NULL, grant_count=0 WHERE id=? AND owner=?")) {
            ps.setInt(1, id); ps.setString(2, user);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    String ownerOf(int id) {
        try (Connection c = Db.get()) { return owner(c, id); } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private String owner(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT owner FROM " + table + " WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
        }
    }

    private int update(Connection c, String sql, String user, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, user); ps.setInt(2, id);
            return ps.executeUpdate();
        }
    }

    private int count(String sql) {
        try (Connection c = Db.get(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next(); return rs.getInt(1);
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    int bookedSeats() { return count("SELECT COUNT(*) FROM " + table + " WHERE owner IS NOT NULL"); }

    int doubleBookings() { return count("SELECT COALESCE(SUM(grant_count-1),0) FROM " + table + " WHERE grant_count > 1"); }

    String seatMap() {
        StringBuilder sb = new StringBuilder("    ");
        for (int i = 1; i <= 10; i++) sb.append(String.format("%-4d", i));
        try (Connection c = Db.get(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, owner FROM " + table + " ORDER BY id")) {
            while (rs.next()) {
                int id = rs.getInt(1);
                if (id % 10 == 0) sb.append("\n").append((char) ('A' + id / 10)).append("   ");
                sb.append(rs.getString(2) == null ? "[ ] " : "[X] ");
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return sb.toString();
    }
}