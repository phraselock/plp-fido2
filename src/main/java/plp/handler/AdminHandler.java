package plp.handler;

import com.ipoxo.plcore.lib.Log;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import plp.backend.PLPApplication;
import plp.lib.CredentialDao;
import plp.lib.PendingUserDao;
import plp.ui.AdminPage;

public class AdminHandler
{
  public void registerRoutes(JavalinConfig config)
  {
    // ── Users ───────────────────────────────────────────────────────────────

    config.routes.get("/admin/users", ctx ->
    {
      ctx.contentType("text/html; charset=UTF-8");
      ctx.result(AdminPage.renderUsers(PendingUserDao.findAll()));
    });

    config.routes.get("/admin/user", ctx ->
    {
      Integer id = intParam(ctx.queryParam("id"));
      PendingUserDao.PendingUser u = id != null ? PendingUserDao.findById(id) : null;
      if (u == null)
      {
        ctx.redirect(adminUrl(ctx, "/admin/users", null));
        return;
      }
      ctx.contentType("text/html; charset=UTF-8");
      ctx.result(AdminPage.renderUser(u, CredentialDao.findByUserId(u.userId())));
    });

    config.routes.post("/admin/users/create", ctx ->
    {
      String email = ctx.formParam("email");
      if (email != null && !email.isBlank())
      {
        PendingUserDao.create(email);
        Log.i("[AdminHandler] User created: " + email);
      }
      ctx.redirect(adminUrl(ctx, "/admin/users", null));
    });

    config.routes.post("/admin/users/newcode", ctx ->
    {
      Integer id = intParam(ctx.formParam("id"));
      if (id != null)
      {
        PendingUserDao.newToken(id);
        Log.i("[AdminHandler] New registration code: id=" + id);
      }
      if (id != null && "user".equals(ctx.formParam("back")))
        ctx.redirect(adminUrl(ctx, "/admin/user", "id=" + id));
      else
        ctx.redirect(adminUrl(ctx, "/admin/users", null));
    });

    // Deletes the user with all registered keys and sessions.
    config.routes.post("/admin/users/delete", ctx ->
    {
      Integer id = intParam(ctx.formParam("id"));
      if (id != null)
      {
        PendingUserDao.deleteById(id);
        Log.i("[AdminHandler] User deleted: id=" + id);
      }
      ctx.redirect(adminUrl(ctx, "/admin/users", null));
    });

    // ── Keys of a user ──────────────────────────────────────────────────────

    config.routes.post("/admin/credential/rename", ctx ->
    {
      Integer id          = intParam(ctx.formParam("id"));
      String  displayName = ctx.formParam("displayName");
      if (id != null)
      {
        CredentialDao.updateDisplayName(id, displayName != null ? displayName : "");
        Log.i("[AdminHandler] Display name updated: credential id=" + id);
      }
      ctx.redirect(adminUrl(ctx, "/admin/user", "id=" + ctx.formParam("user")));
    });

    config.routes.post("/admin/credential/delete", ctx ->
    {
      Integer id = intParam(ctx.formParam("id"));
      if (id != null)
      {
        CredentialDao.deleteById(id);
        Log.i("[AdminHandler] Credential deleted: id=" + id);
      }
      ctx.redirect(adminUrl(ctx, "/admin/user", "id=" + ctx.formParam("user")));
    });
  }

  private static Integer intParam(String s)
  {
    try { return s != null ? Integer.parseInt(s.trim()) : null; }
    catch (NumberFormatException e) { return null; }
  }

  // Builds an admin redirect URL, preserving the token query param.
  private static String adminUrl(Context ctx, String path, String query)
  {
    String tok = ctx.queryParam("token");
    StringBuilder url = new StringBuilder(PLPApplication.PATH_PREFIX + path);
    char sep = '?';
    if (query != null)
    {
      url.append(sep).append(query);
      sep = '&';
    }
    if (tok != null && !tok.isBlank())
    {
      url.append(sep).append("token=").append(tok);
    }
    return url.toString();
  }
}
