package plp.lib;

import com.ipoxo.plcore.lib.Log;
import java.security.SecureRandom;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

// Users pre-registered by the admin. Each user has a fixed WebAuthn user ID;
// every key (credential) registered with a code is stored under that ID.
public final class PendingUserDao
{
  private static final String TOKEN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final SecureRandom RNG   = new SecureRandom();

  public record PendingUser(int id, String email, String userId, String regToken,
                            boolean used, int keyCount) {}

  private static final String SELECT =
    "SELECT p.id, p.email, p.user_id, p.reg_token, p.used, " +
    "       (SELECT COUNT(*) FROM credentials c WHERE c.user_id = p.user_id) AS key_count " +
    "FROM pending_users p ";

  public static List<PendingUser> findAll()
  {
    List<PendingUser> result = new ArrayList<>();
    String sql = SELECT + "ORDER BY p.email";
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery())
    {
      while (rs.next())
        result.add(map(rs));
    } catch (SQLException e) {
      Log.e("Exception PendingUserDao.findAll: " + e.getLocalizedMessage());
    }
    return result;
  }

  public static PendingUser findById(int id)
  {
    String sql = SELECT + "WHERE p.id = ?";
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql))
    {
      ps.setInt(1, id);
      try (ResultSet rs = ps.executeQuery()) { return rs.next() ? map(rs) : null; }
    } catch (SQLException e) {
      Log.e("Exception PendingUserDao.findById: " + e.getLocalizedMessage());
      return null;
    }
  }

  public static PendingUser findByEmail(String email)
  {
    String sql = SELECT + "WHERE p.email = ?";
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql))
    {
      ps.setString(1, email.trim().toLowerCase());
      try (ResultSet rs = ps.executeQuery()) { return rs.next() ? map(rs) : null; }
    } catch (SQLException e) {
      Log.e("Exception PendingUserDao.findByEmail: " + e.getLocalizedMessage());
      return null;
    }
  }

  // Creates a user with a new user ID and registration code.
  // An existing user is left unchanged (use newToken for another key).
  public static void create(String email)
  {
    String sql = "INSERT OR IGNORE INTO pending_users (email, user_id, reg_token, used) VALUES (?, ?, ?, 0)";
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql))
    {
      ps.setString(1, email.trim().toLowerCase());
      ps.setString(2, PLTool.byteArrayToHexString(PLTool.generateRandom(32)));
      ps.setString(3, generateToken());
      ps.executeUpdate();
    } catch (SQLException e) {
      Log.e("Exception PendingUserDao.create: " + e.getLocalizedMessage());
    }
  }

  // New registration code for an existing user (e.g. to add a reserve key).
  public static void newToken(int id)
  {
    String sql = "UPDATE pending_users SET reg_token = ?, used = 0, created_at = unixepoch() WHERE id = ?";
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql))
    {
      ps.setString(1, generateToken());
      ps.setInt(2, id);
      ps.executeUpdate();
    } catch (SQLException e) {
      Log.e("Exception PendingUserDao.newToken: " + e.getLocalizedMessage());
    }
  }

  // Deletes the user together with all of its keys and sessions.
  public static void deleteById(int id)
  {
    PendingUser u = findById(id);
    if (u == null) return;
    try (Connection conn = DB.open())
    {
      conn.setAutoCommit(false);
      try (PreparedStatement c = conn.prepareStatement("DELETE FROM credentials WHERE user_id = ?");
           PreparedStatement s = conn.prepareStatement("DELETE FROM sessions WHERE user_id = ?");
           PreparedStatement p = conn.prepareStatement("DELETE FROM pending_users WHERE id = ?"))
      {
        c.setString(1, u.userId()); c.executeUpdate();
        s.setString(1, u.userId()); s.executeUpdate();
        p.setInt(1, id);            p.executeUpdate();
      }
      conn.commit();
    } catch (SQLException e) {
      Log.e("Exception PendingUserDao.deleteById: " + e.getLocalizedMessage());
    }
  }

  public static boolean isValidToken(String email, String token)
  {
    String sql = "SELECT id FROM pending_users WHERE email = ? AND reg_token = ? AND used = 0";
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql))
    {
      ps.setString(1, email.trim().toLowerCase());
      ps.setString(2, token.trim().toUpperCase());
      try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
    } catch (SQLException e) {
      Log.e("Exception PendingUserDao.isValidToken: " + e.getLocalizedMessage());
      return false;
    }
  }

  public static void markUsed(String email)
  {
    String sql = "UPDATE pending_users SET used = 1 WHERE email = ?";
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql))
    {
      ps.setString(1, email.trim().toLowerCase());
      ps.executeUpdate();
    } catch (SQLException e) {
      Log.e("Exception PendingUserDao.markUsed: " + e.getLocalizedMessage());
    }
  }

  private static PendingUser map(ResultSet rs) throws SQLException
  {
    return new PendingUser(rs.getInt("id"), rs.getString("email"), rs.getString("user_id"),
                           rs.getString("reg_token"), rs.getInt("used") != 0, rs.getInt("key_count"));
  }

  private static String generateToken()
  {
    StringBuilder sb = new StringBuilder(8);
    for (int i = 0; i < 8; i++)
      sb.append(TOKEN_CHARS.charAt(RNG.nextInt(TOKEN_CHARS.length())));
    return sb.toString();
  }
}
