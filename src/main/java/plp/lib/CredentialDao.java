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


  // Stores one registered key (credential) for a user. Returns false on error
  // (e.g. the same credential ID is already registered).
  public static boolean insert(String userid, String displayname, String domain,
                               String credentialId, String credentialRecord,
                               String publicKey, long signCounter, String authenticator)
  {
    String sql = """
            INSERT INTO credentials (
              user_id, display_name, domain, credential_id,
              credential_Record, public_key, signature_counter, authenticator
            )
            VALUES (?,?,?,?,?,?,?,?)
        """;

    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql))
    {
      ps.setString(1, userid);
      ps.setString(2, displayname);
      ps.setString(3, domain);
      ps.setString(4, credentialId);
      ps.setString(5, credentialRecord);
      ps.setString(6, publicKey);
      ps.setLong(7, signCounter);
      ps.setString(8, authenticator);
      ps.executeUpdate();
      return true;
    } catch (SQLException e) {
      Log.e ("Exception CredentialDao.insert: " + e.getLocalizedMessage());
      return false;
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
    String sql =
      "SELECT p.user_id, p.email AS user, " +
      "       (SELECT c.display_name FROM credentials c WHERE c.user_id = p.user_id " +
      "        ORDER BY c.id DESC LIMIT 1) AS display_name " +
      "FROM pending_users p WHERE p.user_id = ?";
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

  public record CredentialSummary(int id, String credentialId, String displayName,
                                  String domain, long createdAt, String authenticator) {}

  public static List<CredentialSummary> findByUserId(String userId)
  {
    List<CredentialSummary> result = new ArrayList<>();
    String sql = "SELECT id, credential_id, display_name, domain, created_at, authenticator " +
                 "FROM credentials WHERE user_id = ? ORDER BY id";
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql))
    {
      ps.setString(1, userId);
      try (ResultSet rs = ps.executeQuery())
      {
        while (rs.next())
          result.add(new CredentialSummary(rs.getInt("id"), rs.getString("credential_id"),
                                           rs.getString("display_name"), rs.getString("domain"),
                                           rs.getLong("created_at"), rs.getString("authenticator")));
      }
    } catch (SQLException e) {
      Log.e("Exception CredentialDao.findByUserId: " + e.getLocalizedMessage());
    }
    return result;
  }

  public static void updateDisplayName(int id, String displayName)
  {
    String sql = "UPDATE credentials SET display_name = ? WHERE id = ?";
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql))
    {
      ps.setString(1, displayName);
      ps.setInt(2, id);
      ps.executeUpdate();
    } catch (SQLException e) {
      Log.e("Exception CredentialDao.updateDisplayName: " + e.getLocalizedMessage());
    }
  }

  public static void deleteById(int id)
  {
    String sql = "DELETE FROM credentials WHERE id = ?";
    try (Connection conn = DB.open();
         PreparedStatement ps = conn.prepareStatement(sql))
    {
      ps.setInt(1, id);
      ps.executeUpdate();
    } catch (SQLException e) {
      Log.e("Exception CredentialDao.deleteById: " + e.getLocalizedMessage());
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
