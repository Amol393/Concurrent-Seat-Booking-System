import java.io.*;
import java.sql.*;
import java.util.Properties;

class Db {
    static final String HOST, PORT, DATABASE, USER, PASS, URL, SERVER_URL;
    static final boolean SQLITE;

    static {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream("db.properties")) { p.load(in); }
        catch (IOException e) { }

        HOST     = p.getProperty("host", "localhost").trim();
        PORT     = p.getProperty("port", "3306").trim();
        DATABASE = p.getProperty("database", "booking").trim();
        USER     = env("DB_USER", p.getProperty("user", "root").trim());
        PASS     = env("DB_PASS", p.getProperty("password", "").trim());

        String extra = "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        String url = env("DB_URL", p.getProperty("url", "").trim());
        if (url.isEmpty()) {
            URL = "jdbc:mysql://" + HOST + ":" + PORT + "/" + DATABASE + "?" + extra;
            SERVER_URL = "jdbc:mysql://" + HOST + ":" + PORT + "/?" + extra;
        } else {
            URL = url;
            SERVER_URL = null;
        }
        SQLITE = URL.startsWith("jdbc:sqlite:");
    }

    private static String env(String k, String d) {
        String v = System.getenv(k);
        return (v == null || v.isBlank()) ? d : v;
    }

    static String describe() {
        return SQLITE ? "SQLite file (" + URL + ")"
                      : "MySQL at " + HOST + ", port " + PORT + ", database '" + DATABASE + "', user '" + USER + "'";
    }

    static Connection get() throws SQLException {
        Connection c = SQLITE ? DriverManager.getConnection(URL) : DriverManager.getConnection(URL, USER, PASS);
        if (SQLITE) try (Statement st = c.createStatement()) {
            st.execute("PRAGMA busy_timeout=60000");
        }
        return c;
    }

    static void createDatabase() throws SQLException {
        if (SQLITE) return;
        try (Connection c = DriverManager.getConnection(SERVER_URL, USER, PASS);
             Statement st = c.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + DATABASE + "`");
        }
    }

    static void createTable(String table) throws SQLException {
        createDatabase();
        try (Connection c = get(); Statement st = c.createStatement()) {
            if (SQLITE) st.execute("PRAGMA journal_mode=WAL");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "id INTEGER PRIMARY KEY, "
                    + "owner VARCHAR(50) NULL, "
                    + "grant_count INTEGER NOT NULL DEFAULT 0)");
        }
    }

    static void reset(String table, int n) throws SQLException {
        try (Connection c = get()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) { st.executeUpdate("DELETE FROM " + table); }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + table + "(id) VALUES (?)")) {
                for (int i = 0; i < n; i++) { ps.setInt(1, i); ps.addBatch(); }
                ps.executeBatch();
            }
            c.commit();
        }
    }

    static String explain(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) root = root.getCause();
        String m = String.valueOf(root.getMessage());
        String s = String.valueOf(e.getMessage()) + " " + m;
        if (s.contains("No suitable driver"))
            return "The MySQL connector jar is missing. Copy mysql-connector-j-8.x.jar into the lib folder.";
        if (s.contains("Communications link failure") || s.contains("Connection refused"))
            return "Cannot reach MySQL at " + HOST + ":" + PORT + ". Is the MySQL server running, and is the port correct in db.properties?";
        if (s.contains("Access denied"))
            return "MySQL rejected the user/password. Fix 'user' and 'password' in db.properties.";
        return m;
    }
}