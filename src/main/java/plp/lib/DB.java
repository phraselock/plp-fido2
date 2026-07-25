package plp.lib;

import com.ipoxo.plcore.lib.Log;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;

public class DB {

  private static final String DB_NAME             = "phraselockWebAuthn.db";
  private static final int CURRENT_DB_VERSION     = 3;

  private static Path resolveDbPath() {
    try {
      Path jarDir = Paths.get(
        DB.class.getProtectionDomain()
          .getCodeSource()
          .getLocation()
          .toURI()
      ).getParent();

      return jarDir.resolve(DB_NAME);
    } catch (Exception e) {
      Log.e ("Exception DB.resolveDbPath: " + e.getLocalizedMessage());
      return null;
    }
  }

  public static Connection open() throws SQLException {
    try {
      Path dbPath = resolveDbPath();
      String jdbcUrl = "jdbc:sqlite:" + dbPath.toString();
      return DriverManager.getConnection(jdbcUrl);
    }catch (Exception e){
      Log.e ("Exception DB.open: " + e.getLocalizedMessage());
      return null;
    }
  }

  public static void initDB()
  {
    boolean RESET_DB    = false;
    try (Connection connection = DB.open())
    {
      Path dbPath = resolveDbPath();
      if (Files.exists(dbPath) && connection!=null)
      {
        int currentDBVersion = getDBVersion();

        checkDBVersion(false);

        checkExsist_credentials(RESET_DB);
        checkExist_pendingUsers(RESET_DB);
        checkExist_sessions(RESET_DB);

        if(currentDBVersion!= CURRENT_DB_VERSION)
        {
          setDBVersion(CURRENT_DB_VERSION);
        }
      }
    } catch (Exception e) {
      Log.e ("Exception DB.initDB: " + e.getLocalizedMessage());
    }
  }

  public static void checkDBVersion(boolean forceReset)
  {
    if (forceReset)
    {
      try (Connection conn = DB.open())
      {
        conn.setAutoCommit(false);
        try(Statement st = conn.createStatement())
        {
          st.executeUpdate("DROP TABLE IF EXISTS dbversion;");
        }
        conn.commit();
      } catch (SQLException e) {
        Log.e ("Exception (1)DB.checkDBVersion: " + e.getLocalizedMessage());
      }
    }

    try (Connection conn = DB.open();
         PreparedStatement stmt = conn.prepareStatement(
           "SELECT name FROM sqlite_master WHERE type='table' AND name=?"))
    {
      stmt.setString(1, "dbversion");
      try (ResultSet rs = stmt.executeQuery())
      {
        if (!rs.next() || forceReset)
        {
          conn.setAutoCommit(false);
          try(Statement st = conn.createStatement())
          {
            st.executeUpdate("""
            CREATE TABLE IF NOT EXISTS dbversion ( 
            idx				INTEGER NOT NULL PRIMARY KEY, 
            dbv				INTEGER DEFAULT '0' );
            """);
          }
          conn.commit();
        }
      }
    } catch (SQLException e) {
      Log.e ("Exception (2)DB.checkDBVersion: " + e.getLocalizedMessage());
    }
  }

  public static void setDBVersion(int dbVersion)
  {
    String sql = "INSERT OR REPLACE INTO dbversion (idx, dbv) VALUES ( ?, ? );";
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql))
    {
      ps.setInt(1, 1);
      ps.setInt(2, dbVersion);
      ps.executeUpdate();
    } catch (SQLException e) {
      Log.e ("Exception DB.setDBVersion: " + e.getLocalizedMessage());
    }
  }

  public static int getDBVersion()
  {
    String sql = "SELECT dbv FROM dbversion WHERE idx=1;";
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql)) {

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return -1;
        }
        return rs.getInt("dbv");
      }
    } catch (SQLException e) {
      Log.e("Exception DB.getDBVersion: " + e.getLocalizedMessage());
      return 0;
    }
  }

  public static void checkExist_pendingUsers(boolean forceReset)
  {
    if (forceReset)
    {
      try (Connection conn = DB.open())
      {
        conn.setAutoCommit(false);
        try (Statement st = conn.createStatement())
        {
          st.executeUpdate("DROP TABLE IF EXISTS pending_users;");
        }
        conn.commit();
      } catch (SQLException e) {
        Log.e("Exception DB.checkExist_pendingUsers (reset): " + e.getLocalizedMessage());
      }
    }

    try (Connection conn = DB.open();
         PreparedStatement stmt = conn.prepareStatement(
           "SELECT name FROM sqlite_master WHERE type='table' AND name=?"))
    {
      stmt.setString(1, "pending_users");
      try (ResultSet rs = stmt.executeQuery())
      {
        if (!rs.next())
        {
          conn.setAutoCommit(false);
          try (Statement st = conn.createStatement())
          {
            st.executeUpdate("""
                CREATE TABLE pending_users (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    email      TEXT NOT NULL UNIQUE,
                    reg_token  TEXT NOT NULL,
                    created_at INTEGER DEFAULT (unixepoch()),
                    used       INTEGER DEFAULT 0
                );
            """);
          }
          conn.commit();
        }
      }
    } catch (SQLException e) {
      Log.e("Exception DB.checkExist_pendingUsers: " + e.getLocalizedMessage());
    }
  }

  public static void checkExsist_credentials(boolean forceReset)
  {
    if (forceReset)
    {
      try (Connection conn = DB.open())
      {
        conn.setAutoCommit(false);
        try(Statement st = conn.createStatement())
        {
          st.executeUpdate("DROP INDEX IF EXISTS credentials_idx01;");
          st.executeUpdate("DROP INDEX IF EXISTS credentials_idx02;");
          st.executeUpdate("DROP TABLE IF EXISTS credentials;");
        }
        conn.commit();
      } catch (SQLException e) {
        Log.e ("Exception DB.checkExsist_credentials: " + e.getLocalizedMessage());
      }
    }

    try (Connection conn =  DB.open();
         PreparedStatement stmt = conn.prepareStatement(
           "SELECT name FROM sqlite_master WHERE type='table' AND name=?"
         ))
    {
      stmt.setString(1, "credentials");

      try (ResultSet rs = stmt.executeQuery())
      {
        if (!rs.next())
        {
          conn.setAutoCommit(false);
          try(Statement st = conn.createStatement())
          {
            st.executeUpdate("""
                  CREATE TABLE credentials (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      user_id TEXT NOT NULL,
                      user TEXT NOT NULL,
                      display_name TEXT DEFAULT NULL,
                      domain TEXT DEFAULT NULL,
                      credential_id TEXT DEFAULT NULL,
                      credential_Record TEXT DEFAULT NULL,
                      public_key TEXT DEFAULT NULL,
                      signature_counter INTEGER DEFAULT 0,
                      discoverable INTEGER DEFAULT 0
                  );
            """);

            st.executeUpdate("CREATE UNIQUE INDEX credentials_idx01 ON credentials (user_id);");
            st.executeUpdate("CREATE UNIQUE INDEX credentials_idx02 ON credentials (credential_id);");
          }
          conn.commit();
        }
      }
    } catch (SQLException e) {
      Log.e ("Exception DB.checkExsist_credentials: " + e.getLocalizedMessage());
    }
  }

  public static void checkExist_sessions(boolean forceReset)
  {
    if (forceReset)
    {
      try (Connection conn = DB.open())
      {
        conn.setAutoCommit(false);
        try (Statement st = conn.createStatement())
        {
          st.executeUpdate("DROP TABLE IF EXISTS sessions;");
        }
        conn.commit();
      } catch (SQLException e) {
        Log.e("Exception DB.checkExist_sessions (reset): " + e.getLocalizedMessage());
      }
    }

    try (Connection conn = DB.open();
         PreparedStatement stmt = conn.prepareStatement(
           "SELECT name FROM sqlite_master WHERE type='table' AND name=?"))
    {
      stmt.setString(1, "sessions");
      try (ResultSet rs = stmt.executeQuery())
      {
        if (!rs.next())
        {
          conn.setAutoCommit(false);
          try (Statement st = conn.createStatement())
          {
            st.executeUpdate("""
                CREATE TABLE sessions (
                    id         TEXT PRIMARY KEY,
                    user_id    TEXT NOT NULL,
                    created_at INTEGER DEFAULT (unixepoch())
                );
            """);
          }
          conn.commit();
        }
      }
    } catch (SQLException e) {
      Log.e("Exception DB.checkExist_sessions: " + e.getLocalizedMessage());
    }
  }


}
