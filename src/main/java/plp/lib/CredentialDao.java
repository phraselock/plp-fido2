package plp.lib;

import com.ipoxo.plcore.lib.Log;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class CredentialDao {


  public record StoredCredential(
    String userId,
    String credentialId,
    String publicKey,
    String credentialRecord,
    long signCount
  ){}


  public static void setUser(String userid, String user, String displayname, String domain) {
    String sql = """
            INSERT INTO credentials (
              user_id,user, display_name,domain
            )
            VALUES (?,?,?,?)
        """;

    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql))
    {
      ps.setString(1, userid);
      ps.setString(2, user);
      ps.setString(3, displayname);
      ps.setString(4, domain);

      ps.executeUpdate();
    } catch (SQLException e) {
      Log.e ("Exception CredentialDao.setUser: " + e.getLocalizedMessage());
    }
  }

  public static void setCredentialId(String userid, String credentialId) {
    String sql = """
            UPDATE credentials
            SET credential_id = ?
            WHERE user_id = ?
        """;
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setString(1, credentialId);
      ps.setString(2, userid);
      ps.executeUpdate();

    } catch (SQLException e) {
      Log.e ("Exception CredentialDao.setCredentialId: " + e.getLocalizedMessage());
    }
  }

  public static void setPublicKey(String userid, String publicKey) {
    String sql = """
            UPDATE credentials
            SET public_key = ?
            WHERE user_id = ?
        """;
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setString(1, publicKey);
      ps.setString(2, userid);
      ps.executeUpdate();

    } catch (SQLException e) {
      Log.e ("Exception CredentialDao.setPublicKey: " + e.getLocalizedMessage());
    }
  }

  public static void setCredentialRecord(String userid, String credentialRecord) {
    String sql = """
            UPDATE credentials
            SET credential_Record = ?
            WHERE user_id = ?
        """;
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setString(1, credentialRecord);
      ps.setString(2, userid);
      ps.executeUpdate();

    } catch (SQLException e) {
      Log.e ("Exception CredentialDao.setCredentialRecord: " + e.getLocalizedMessage());
    }
  }
  public static void setSignCounter(String userid, long signCounter) {
    String sql = """
            UPDATE credentials
            SET signature_counter = ?
            WHERE user_id = ?
        """;
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setLong(1, signCounter);
      ps.setString(2, userid);
      ps.executeUpdate();

    } catch (SQLException e) {
      Log.e ("Exception CredentialDao.setSignCounter: " + e.getLocalizedMessage());
    }
  }

  public static void updateSignatureCounter(String credentialId, long newCounter)
  {
    String sql = """
            UPDATE credentials
            SET signature_counter = ?
            WHERE credential_id = ?
        """;

    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setLong(1, newCounter);
      ps.setString(2, credentialId);
      ps.executeUpdate();

    } catch (SQLException e) {
      Log.e ("Exception CredentialDao.updateSignatureCounter: " + e.getLocalizedMessage());
    }
  }

  public record UserInfo(String userId, String user, String displayName) {}

  public static UserInfo findUserInfoById(String userId)
  {
    String sql = "SELECT user_id, user, display_name FROM credentials WHERE user_id = ?";
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql))
    {
      ps.setString(1, userId);
      try (ResultSet rs = ps.executeQuery())
      {
        if (!rs.next()) return null;
        return new UserInfo(rs.getString("user_id"), rs.getString("user"), rs.getString("display_name"));
      }
    } catch (SQLException e) {
      Log.e("Exception CredentialDao.findUserInfoById: " + e.getLocalizedMessage());
      return null;
    }
  }

  public record CredentialSummary(String userId, String user, String displayName, String domain) {}

  public static List<CredentialSummary> findAll()
  {
    List<CredentialSummary> result = new ArrayList<>();
    String sql = "SELECT user_id, user, display_name, domain FROM credentials ORDER BY user";
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery())
    {
      while (rs.next())
        result.add(new CredentialSummary(rs.getString("user_id"), rs.getString("user"),
                                         rs.getString("display_name"), rs.getString("domain")));
    } catch (SQLException e) {
      Log.e("Exception CredentialDao.findAll: " + e.getLocalizedMessage());
    }
    return result;
  }

  public static void updateDisplayName(String userId, String displayName)
  {
    String sql = "UPDATE credentials SET display_name = ? WHERE user_id = ?";
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql))
    {
      ps.setString(1, displayName);
      ps.setString(2, userId);
      ps.executeUpdate();
    } catch (SQLException e) {
      Log.e("Exception CredentialDao.updateDisplayName: " + e.getLocalizedMessage());
    }
  }

  public static void deleteByUserId(String userId)
  {
    String sql = "DELETE FROM credentials WHERE user_id = ?";
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql))
    {
      ps.setString(1, userId);
      ps.executeUpdate();
    } catch (SQLException e) {
      Log.e("Exception CredentialDao.deleteByUserId: " + e.getLocalizedMessage());
    }
  }

  public static StoredCredential findByCredentialId(String credentialId)
  {
    String sql = """
            SELECT  user_id, 
                    credential_id,
                    public_key,
                    credential_Record,
                    signature_counter
            FROM credentials
            WHERE credential_id = ?
        """;

    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setString(1, credentialId);

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return null;
        }

        return new StoredCredential(
          rs.getString("user_id"),
          rs.getString("credential_id"),
          rs.getString("public_key"),
          rs.getString("credential_Record"),
          rs.getInt("signature_counter")
        );
      }
    } catch (SQLException e) {
      Log.e ("Exception CredentialDao.StoredCredential: " + e.getLocalizedMessage());
      return null;
    }
  }


}
