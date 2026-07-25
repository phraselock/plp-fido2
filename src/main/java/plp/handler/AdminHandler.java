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
    config.routes.get("/admin/users", ctx ->
    {
      ctx.contentType("text/html; charset=UTF-8");
      ctx.result(AdminPage.render(
        PendingUserDao.findAll(),
        CredentialDao.findAll()
      ));
    });

    config.routes.post("/admin/users/create", ctx ->
    {
      String email = ctx.formParam("email");
      if (email != null && !email.isBlank())
      {
        PendingUserDao.create(email);
        Log.i("[AdminHandler] Pending user created: " + email);
      }
      ctx.redirect(adminUrl(ctx, ""));
    });

    config.routes.post("/admin/users/delete", ctx ->
    {
      String idParam = ctx.formParam("id");
      if (idParam != null)
      {
        try
        {
          PendingUserDao.deleteById(Integer.parseInt(idParam));
          Log.i("[AdminHandler] Pending user deleted: id=" + idParam);
        }
        catch (NumberFormatException ignored) {}
      }
      ctx.redirect(adminUrl(ctx, ""));
    });

    config.routes.post("/admin/credential/rename", ctx ->
    {
      String userId      = ctx.formParam("userId");
      String displayName = ctx.formParam("displayName");
      if (userId != null && !userId.isBlank())
      {
        CredentialDao.updateDisplayName(userId, displayName != null ? displayName : "");
        Log.i("[AdminHandler] Display name updated: userId=" + userId);
      }
      ctx.redirect(adminUrl(ctx, "#credentials"));
    });

    config.routes.post("/admin/credential/delete", ctx ->
    {
      String userId = ctx.formParam("userId");
      if (userId != null && !userId.isBlank())
      {
        CredentialDao.deleteByUserId(userId);
        Log.i("[AdminHandler] Credential deleted: userId=" + userId);
      }
      ctx.redirect(adminUrl(ctx, "#credentials"));
    });
  }

  // Builds the redirect URL for /admin/users, preserving the token query param.
  private static String adminUrl(Context ctx, String suffix)
  {
    String tok  = ctx.queryParam("token");
    String base = PLPApplication.PATH_PREFIX + "/admin/users";
    return tok != null && !tok.isBlank() ? base + "?token=" + tok + suffix : base + suffix;
  }
}
