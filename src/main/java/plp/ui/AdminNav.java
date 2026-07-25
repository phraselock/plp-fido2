package plp.ui;

import j2html.tags.DomContent;
import plp.backend.PLPApplication;

import static j2html.TagCreator.*;

public class AdminNav
{
  public static DomContent bar()
  {
    return div().with(
      div()
        .withStyle("display:flex;gap:8px;flex-wrap:wrap;margin-bottom:28px;align-items:center;")
        .with(
          span("FIDO2 Admin")
            .withStyle(
              "padding:4px 14px;border-radius:20px;font-size:12px;" +
              "border:1px solid #444;background:#333;color:#fff;"),
          img().withSrc(PLPApplication.PATH_PREFIX + "/img/logo.png")
            .withAlt("Logo")
            .withStyle(
              "max-height:48px;max-width:160px;object-fit:contain;" +
              "border-radius:8px;margin-left:auto;")
        ),

      script(rawHtml(
        "document.addEventListener('DOMContentLoaded',function(){" +
        "  var prefix='" + PLPApplication.PATH_PREFIX + "';" +
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
}
