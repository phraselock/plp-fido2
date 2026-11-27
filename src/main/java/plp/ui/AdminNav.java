package plp.ui;

import j2html.tags.DomContent;
import plp.backend.PLPApplication;

import static j2html.TagCreator.*;

public class AdminNav
{
  private static final String P = PLPApplication.PATH_PREFIX;

  // Main frame around nav + page content (same look as plp-admin).
  public static final String FRAME =
    "max-width:1024px;margin:28px auto 0 auto;padding:30px;" +
    "background:white;border:0.5px solid #c0c0c0;" +
    "border-radius:12px;box-shadow:0 2px 6px rgba(0,0,0,0.1);";

  // active: "appconfig" | "users"
  public static DomContent bar(String active)
  {
    return div().with(
      div()
        .withStyle("display:flex;gap:8px;flex-wrap:wrap;margin-bottom:28px;align-items:center;")
        .with(
          tile(P + "/admin/appconfig", "Application", active.equals("appconfig")),
          tile(P + "/admin/users",     "Users",       active.equals("users")),
          img().withSrc(P + "/img/logo.png")
            .withAlt("Logo")
            .withStyle(
              "max-height:48px;max-width:160px;object-fit:contain;" +
              "border-radius:8px;margin-left:auto;")
        ),

      script(rawHtml(
        "document.addEventListener('DOMContentLoaded',function(){" +
        "  var prefix='" + P + "';" +
        "  var t=(new URLSearchParams(window.location.search)).get('token');" +
        "  if(!t)return;" +
        "  document.querySelectorAll('a[href]').forEach(function(a){" +
        "    var h=a.getAttribute('href');" +
        "    if(h&&h.startsWith(prefix+'/admin/')){" +
        "      var u=new URL(a.href,location.origin);" +
        "      u.searchParams.set('token',t);" +
        "      a.href=u.toString();" +
        "    }" +
        "  });" +
        "  document.querySelectorAll('form').forEach(function(f){" +
        "    var action=f.getAttribute('action')||'';" +
        "    if(action.startsWith(prefix+'/admin/')){" +
        "      var u=new URL(f.action,location.origin);" +
        "      u.searchParams.set('token',t);" +
        "      f.action=u.toString();" +
        "    }" +
        "  });" +
        "});"
      ))
    );
  }

  public static DomContent footer()
  {
    int year = java.time.LocalDate.now().getYear();
    return div()
      .withStyle(
        "max-width:1024px;margin:16px auto 40px auto;" +
        "text-align:center;font-size:11px;color:#aaa;")
      .withText("© " + year + " iPoxo IT GmbH — All rights reserved");
  }

  private static DomContent tile(String href, String label, boolean active)
  {
    return a(label).withHref(href).withClass("nav-tile" + (active ? " active" : ""));
  }

  // Nav pill style (same look as plp-admin). Include in the page <style>.
  public static final String CSS =
    ".nav-tile { padding:4px 14px; border-radius:20px; font-size:12px; text-decoration:none;" +
    "  border:1px solid #bbb; background:#f0f0f0; color:#555; }" +
    ".nav-tile.active { border-color:#444; background:#333; color:#fff; }";
}
