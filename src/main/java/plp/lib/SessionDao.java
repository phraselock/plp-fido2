package plp.lib;

import com.ipoxo.plcore.lib.Log;

import java.security.SecureRandom;
import java.sql.*;
import java.util.Base64;

public final class SessionDao
{
  private static final int SESSION_BYTES = 32;

  public static String create(String userId)
  {
    String sessionId = generateId();
    String sql = "INSERT INTO sessions (id, user_id) VALUES (?, ?)";
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql))
    {
      ps.setString(1, sessionId);
      ps.setString(2, userId);
      ps.executeUpdate();
    } catch (SQLException e) {
      Log.e("Exception SessionDao.create: " + e.getLocalizedMessage());
    }
    return sessionId;
  }

  public static String findUserId(String sessionId)
  {
    if (sessionId == null || sessionId.isBlank()) return null;
    String sql = "SELECT user_id FROM sessions WHERE id = ?";
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql))
    {
      ps.setString(1, sessionId);
      try (ResultSet rs = ps.executeQuery())
      {
        return rs.next() ? rs.getString("user_id") : null;
      }
    } catch (SQLException e) {
      Log.e("Exception SessionDao.findUserId: " + e.getLocalizedMessage());
      return null;
    }
  }

  public static void delete(String sessionId)
  {
    if (sessionId == null || sessionId.isBlank()) return;
    String sql = "DELETE FROM sessions WHERE id = ?";
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql))
    {
      ps.setString(1, sessionId);
      ps.executeUpdate();
    } catch (SQLException e) {
      Log.e("Exception SessionDao.delete: " + e.getLocalizedMessage());
    }
  }

  private static String generateId()
  {
    byte[] bytes = new byte[SESSION_BYTES];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
