package plp.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipoxo.plcore.lib.Log;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.credential.CredentialRecord;
import com.webauthn4j.credential.CredentialRecordImpl;
import com.webauthn4j.data.*;
import com.webauthn4j.data.attestation.AttestationObject;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData;
import com.webauthn4j.data.attestation.authenticator.COSEKey;
import com.webauthn4j.data.attestation.statement.CertificateBaseAttestationStatement;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.Challenge;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;

import io.javalin.http.Cookie;
import io.javalin.http.Context;
import io.javalin.http.SameSite;
import plp.backend.PLPApplication;
import plp.lib.CredentialDao;
import plp.lib.PendingUserDao;
import plp.lib.PLTool;
import plp.lib.SessionDao;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.security.SecureRandom;
import java.util.*;


public class WebAuthn
{
  public static void registerStart(Context ctx)
  {
    Map<String, Object> json = ctx.bodyAsClass(Map.class);

    String displayname = (String) json.get("displayname");
    String user        = (String) json.get("user");
    String rpid        = (String) json.get("rpid");
    String device      = (String) json.get("device");
    String token       = (String) json.get("token");

    // Validate registration token
    if (!PendingUserDao.isValidToken(user, token))
    {
      ctx.status(403).result("Invalid or missing registration token");
      return;
    }

    // Store email for markUsed after finish
    ctx.sessionAttribute("reg_email", user);

    // 1. Generate challenge
    byte[] challengeBytes = new byte[32];
    new SecureRandom().nextBytes(challengeBytes);
    String challengeB64 = PLTool.b64encode2String(challengeBytes);

    // 2. Store challenge (in session)
    ctx.sessionAttribute("challenge", challengeB64);

    // 3. User ID: fixed per user (created by the admin), so every key
    //    registered for this user belongs to the same account.
    PendingUserDao.PendingUser pu = PendingUserDao.findByEmail(user);
    byte[] userId = PLTool.hexStringToByteArray(pu.userId());
    String userIdB64 = PLTool.b64encode2String(userId);

    // Keys already registered for this user: the authenticator refuses to
    // register the same key twice.
    List<Map<String, Object>> excludeCredentials = new ArrayList<>();
    for (CredentialDao.CredentialSummary c : CredentialDao.findByUserId(pu.userId()))
    {
      excludeCredentials.add(Map.of(
        "type", "public-key",
        "id",   PLTool.b64encode2String(PLTool.hexStringToByteArray(c.credentialId()))));
    }

    Map<String, Object> response = Map.of(
      "challenge",  challengeB64,
      "rp", Map.of(
        "name", "PhraseLock",
        "id", rpid
      ),
      // "direct": the authenticator's attestation certificate is passed on;
      // its subject names the authenticator (e.g. Yubico ... Serial ...).
      "attestation", "direct",
      "timeout", 60000,
      "user", Map.of(
        "id", userIdB64,
        "name", user,
        "displayName", displayname
      ),
      "pubKeyCredParams", List.of(
        Map.of("type", "public-key", "alg", -7),  // ES256
        Map.of("type", "public-key", "alg", -257) // RS256
      ),
      "excludeCredentials", excludeCredentials,
      // Roaming only (USB/NFC/BLE hardware keys — no Touch ID / Windows Hello):
      "authenticatorSelection", Map.of(
        "authenticatorAttachment", "cross-platform",
        "residentKey", "required",
        "userVerification", "preferred"
      )
      // Both roaming and platform authenticators (Touch ID, Windows Hello, hardware keys):
      // "authenticatorSelection", Map.of(
      //   "residentKey", "required",
      //   "userVerification", "preferred"
      // )
    );

    ctx.sessionAttribute("userid", pu.userId());
    ctx.sessionAttribute("reg_displayname", displayname);
    ctx.sessionAttribute("reg_rpid", rpid);
    ctx.json(response);
  }

  public static void registerFinish(Context ctx)
  {
    String rpidHost   = ctx.host().split(":")[0];
    String schema = null;
    String port = null;
    schema = ctx.header("X-Forwarded-Proto");
    port = ctx.header("X-Forwarded-Port");
    if(schema==null) schema     = ctx.scheme();
    if(port==null) port     = String.valueOf(ctx.port());

    // 1. Retrieve challenge from session
    String challengeB64 = ctx.sessionAttribute("challenge");
    if (challengeB64 == null) {
      ctx.status(400).json((Map.of("success", false,"reason","No Challenge found")));
      return;
    }
    byte[] challengeBytes = Base64.getUrlDecoder().decode(challengeB64);
    Challenge challenge = new DefaultChallenge(challengeBytes);

    // 2. JSON from browser (PublicKeyCredential)
    String json = ctx.body();

    // 3. WebAuthnManager
    WebAuthnManager manager = WebAuthnManager.createNonStrictWebAuthnManager();

    // 4. Parse RegistrationData from JSON
    RegistrationData registrationData = manager.parseRegistrationResponseJSON(json);

    // 5. Define origin
    Origin origin = new Origin(buildOrigin(schema, rpidHost, port));

    // 6. ServerProperty
    ServerProperty serverProperty = new ServerProperty(
      origin,
      rpidHost,
      challenge,
      null
    );

    // 7. RegistrationParameters
    RegistrationParameters registrationParameters =
      new RegistrationParameters(serverProperty, null, false);

    // 8. Validate
    RegistrationData verified = manager.validate(registrationData, registrationParameters);

    // 9. Extract credential
    AttestationObject attObj = verified.getAttestationObject();
    AuthenticatorData<?> authData = attObj.getAuthenticatorData();

    byte[] credentialId = authData.getAttestedCredentialData().getCredentialId();

    COSEKey coseKey = authData.getAttestedCredentialData().getCOSEKey();

    byte[] publicKey = null;
    try {
      ObjectMapper cbor = new ObjectMapper();
      publicKey = cbor.writeValueAsBytes(coseKey);
    }catch (Exception e){
      ctx.status(400).json((Map.of("success", false,"reason","CBOR Mapping failed")));
    }

    try {
      manager.verify(registrationData, registrationParameters);
    } catch (Exception e) {
      ctx.status(400).json((Map.of("success", false,"reason","Verify registration data failed")));
      return;
    }

    CredentialRecord credentialRecord =
      new CredentialRecordImpl(
        registrationData.getAttestationObject(),
        registrationData.getCollectedClientData(),
        registrationData.getClientExtensions(),
        registrationData.getTransports()
      );

    ObjectConverter objectConverter = new ObjectConverter();
    AttestedCredentialDataConverter attestedCredentialDataConverter = new AttestedCredentialDataConverter(objectConverter);

    // serialize
    byte[] serializedCR = attestedCredentialDataConverter.convert(credentialRecord.getAttestedCredentialData());


    String authenticator = authenticatorName(attObj);

    String userid = ctx.sessionAttribute("userid");
    boolean stored = CredentialDao.insert(
      userid,
      ctx.sessionAttribute("reg_displayname"),
      ctx.sessionAttribute("reg_rpid"),
      PLTool.byteArrayToHexString(credentialId),
      PLTool.byteArrayToHexString(serializedCR),
      PLTool.byteArrayToHexString(publicKey),
      authData.getSignCount(),
      authenticator);
    if (!stored)
    {
      ctx.status(400).json((Map.of("success", false,"reason","Storing credential failed")));
      return;
    }

    // Mark registration token as used
    String regEmail = ctx.sessionAttribute("reg_email");
    if (regEmail != null) PendingUserDao.markUsed(regEmail);

    ctx.result("Registration successful!");
  }

  // Name of the authenticator from the attestation certificate subject
  // ("O · CN"), or null if no certificate was sent (none/self attestation).
  private static String authenticatorName(AttestationObject attObj)
  {
    try
    {
      if (attObj.getAttestationStatement() instanceof CertificateBaseAttestationStatement cb
          && cb.getX5c() != null && !cb.getX5c().isEmpty())
      {
        String subject = cb.getX5c().get(0).getSubjectX500Principal().getName();
        Log.i("[WebAuthn] Attestation certificate subject: " + subject);
        String o = null, cn = null;
        for (Rdn rdn : new LdapName(subject).getRdns())
        {
          if (rdn.getType().equalsIgnoreCase("O"))  o  = rdn.getValue().toString();
          if (rdn.getType().equalsIgnoreCase("CN")) cn = rdn.getValue().toString();
        }
        if (o != null && cn != null) return o + " · " + cn;
        return cn != null ? cn : (o != null ? o : subject);
      }
      Log.i("[WebAuthn] No attestation certificate (" + attObj.getFormat() + ")");
    }
    catch (Exception e)
    {
      Log.e("[WebAuthn] Reading attestation certificate failed: " + e.getMessage());
    }
    return null;
  }

  public static String generateChallenge()
  {
    SecureRandom random = new SecureRandom();
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public static void loginOptions(Context ctx)
  {
    String rpidHost   = ctx.host().split(":")[0];
    Log.i("rpidHost: " + rpidHost);

    // Generate challenge
    String challenge = generateChallenge();

    // Store challenge in session
    ctx.sessionAttribute("webauthn_challenge", challenge);

    // Build response map
    Map<String, Object> resp = new HashMap<>();
    resp.put("challenge", challenge);
    resp.put("rpId", rpidHost);   // your domain
    resp.put("timeout", 60000);
    resp.put("userVerification", "preferred");

    ctx.json(resp);
  }

  public class AssertionResponse {
    public String id;
    public String rawId;
    public String type;

    public Response response;

    public static class Response {
      public String authenticatorData;
      public String clientDataJSON;
      public String signature;
      public String userHandle;
    }
  }

  public static void loginVerify(Context ctx)
  {
    String rpidHost   = ctx.host().split(":")[0];
    String schema = null;
    String port = null;
    schema = ctx.header("X-Forwarded-Proto");
    port = ctx.header("X-Forwarded-Port");
    if(schema==null) schema     = ctx.scheme();
    if(port==null) port     = String.valueOf(ctx.port());

    // 1. Raw JSON from browser
    String json = ctx.body();

    // 2. Challenge from session
    String expectedChallenge = ctx.sessionAttribute("webauthn_challenge");
    if (expectedChallenge == null) {
      ctx.status(400).json((Map.of("success", false,"reason","No challenge")));
      return;
    }

    // 3. WebAuthnManager
    WebAuthnManager manager = WebAuthnManager.createNonStrictWebAuthnManager();

    // Sign counter: by default webauthn4j rejects a counter that did not
    // increase (possible cloned authenticator). With
    // webauthn.signcount.check=false that check is skipped.
    if (!PLPApplication.signCountCheck())
    {
      manager.getAuthenticationDataVerifier().setMaliciousCounterValueHandler(authObj -> {});
    }

    // 4. Parse JSON → AuthenticationData
    AuthenticationData authData;
    try {
      authData = manager.parseAuthenticationResponseJSON(json);
    } catch (Exception e) {
      ctx.status(400).json((Map.of("success", false,"reason","Parse failed")));
      return;
    }

    // 5. Look up credential by credentialId (rawId)
    //String credentialId = authData.getCredentialId().getBase64Url();
    String credentialId = PLTool.byteArrayToHexString(authData.getCredentialId());
    Log.i("   credentialId " + credentialId);


    CredentialDao.StoredCredential credStored = CredentialDao.findByCredentialId(credentialId);
    if (credStored == null) {
      ctx.status(400).json((Map.of("success", false,"reason","Unknown credential")));
      return;
    }

    // 6. ServerProperty bauen
    Origin origin = new Origin(buildOrigin(schema, rpidHost, port));
    ServerProperty serverProperty = new ServerProperty(
      origin,
      rpidHost,
      new DefaultChallenge(expectedChallenge),
      null
    );

    ObjectConverter objectConverter = new ObjectConverter();
    AttestedCredentialDataConverter attestedCredentialDataConverter = new AttestedCredentialDataConverter(objectConverter);
    String stmp = credStored.credentialRecord();
    byte[] sArr = PLTool.hexStringToByteArray(stmp);
    AttestedCredentialData attestedCredentialData = attestedCredentialDataConverter.convert(sArr);

    CredentialRecord credentialRecord =
      new CredentialRecordImpl(null,
        null,
        null,
        null,
        credStored.signCount(),
        attestedCredentialData,
        null,
        null,
        null,
        null );

    List<byte[]> allowCredentials = null;
    boolean userVerificationRequired = true;
    boolean userPresenceRequired = true;

    AuthenticationParameters authenticationParameters =
      new AuthenticationParameters(
        serverProperty,
        credentialRecord,
        allowCredentials,
        userVerificationRequired,
        userPresenceRequired
      );

    // 8. Validate
    try {
      manager.verify(authData, authenticationParameters);
    } catch (Exception e) {
      ctx.status(400).result("Validation failed");
      return;
    }

    // 9. Check userHandle (discoverable credential)
    if (authData.getUserHandle() != null) {
      //String userHandle = new String(authData.getUserHandle().getBytes());
      String userHandle =  PLTool.byteArrayToHexString(authData.getUserHandle());
      if (!userHandle.equals(credStored.userId())) {
        ctx.status(400).result("UserHandle mismatch");
        return;
      }
    }

    // 10. Update sign count
    long updateSignCount = authData.getAuthenticatorData().getSignCount();
    if (updateSignCount > credStored.signCount())
    {
      CredentialDao.updateSignatureCounter(credentialId,updateSignCount);
    }

    // 11. Create persistent session cookie
    String sessionId = SessionDao.create(credStored.userId());
    Cookie sessionCookie = new Cookie("plp_fido2_session", sessionId);
    sessionCookie.setPath("/");
    sessionCookie.setMaxAge(60 * 60 * 24 * 30);
    sessionCookie.setHttpOnly(true);
    sessionCookie.setSameSite(SameSite.STRICT);
    ctx.cookie(sessionCookie);

    ctx.json((Map.of("success", true, "reason", "none")));
  }

  public static void getSession(Context ctx)
  {
    String sessionId = ctx.cookie("plp_fido2_session");
    String userId    = SessionDao.findUserId(sessionId);
    if (userId == null)
    {
      ctx.status(401).result("Not authenticated");
      return;
    }
    CredentialDao.UserInfo info = CredentialDao.findUserInfoById(userId);
    if (info == null)
    {
      ctx.status(401).result("Not authenticated");
      return;
    }
    ctx.json(Map.of(
      "userId",      info.userId(),
      "email",       info.user(),
      "displayName", info.displayName() != null ? info.displayName() : ""
    ));
  }

  public static void logout(Context ctx)
  {
    String sessionId = ctx.cookie("plp_fido2_session");
    SessionDao.delete(sessionId);
    ctx.removeCookie("plp_fido2_session");
    ctx.status(200).result("OK");
  }

  private static String buildOrigin(String schema, String host, String port)
  {
    if ("https".equals(schema) || "443".equals(port) || "80".equals(port))
      return schema + "://" + host;
    return schema + "://" + host + ":" + port;
  }

}