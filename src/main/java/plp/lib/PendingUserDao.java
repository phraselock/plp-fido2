package plp.lib;

import com.ipoxo.plcore.lib.Log;
import java.security.SecureRandom;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class PendingUserDao
{
  private static final String TOKEN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final SecureRandom RNG   = new SecureRandom();

  public record PendingUser(int id, String email, String regToken, boolean used) {}

  public static List<PendingUser> findAll()
  {
    List<PendingUser> result = new ArrayList<>();
    String sql = "SELECT id, email, reg_token, used FROM pending_users ORDER BY created_at DESC";
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery())
    {
      while (rs.next())
        result.add(new PendingUser(rs.getInt("id"), rs.getString("email"),
                                   rs.getString("reg_token"), rs.getInt("used") != 0));
    } catch (SQLException e) {
      Log.e("Exception PendingUserDao.findAll: " + e.getLocalizedMessage());
    }
    return result;
  }

  public static void create(String email)
  {
    String token = generateToken();
    String sql   = "INSERT OR REPLACE INTO pending_users (email, reg_token, used) VALUES (?, ?, 0)";
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql))
    {
      ps.setString(1, email.trim().toLowerCase());
      ps.setString(2, token);
      ps.executeUpdate();
    } catch (SQLException e) {
      Log.e("Exception PendingUserDao.create: " + e.getLocalizedMessage());
    }
  }

  public static void deleteById(int id)
  {
    String sql = "DELETE FROM pending_users WHERE id = ?";
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql))
    {
      ps.setInt(1, id);
      ps.executeUpdate();
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

  private static String generateToken()
  {
    StringBuilder sb = new StringBuilder(8);
    for (int i = 0; i < 8; i++)
      sb.append(TOKEN_CHARS.charAt(RNG.nextInt(TOKEN_CHARS.length())));
    return sb.toString();
  }
}
