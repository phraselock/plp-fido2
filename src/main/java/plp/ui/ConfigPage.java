package plp.ui;

import java.util.List;

import static j2html.TagCreator.*;

public class ConfigPage
{
  private static final String FIELD =
    "padding:10px;margin-top:4px;margin-bottom:15px;" +
    "background:#fafafa;border:1px solid #ddd;border-radius:8px;";

  public static String renderEditor(
    String pageTitle,
    String filename,
    String postUrl,
    String activeNav,
    String content,
    List<String> warnings,
    boolean saved)
  {
    return document(html(
      head(
        meta().withCharset("UTF-8"),
        title(pageTitle + " — FIDO2 Admin"),
        style(AdminNav.CSS)
      ),
      body()
        .withStyle("font-family:Arial,Helvetica,sans-serif;background-color:#f5f5f5;margin:0;padding:32px;")
        .with(
          div().withStyle(AdminNav.FRAME).with(
            AdminNav.bar(activeNav),
            h1(pageTitle)
              .withStyle("text-align:center;margin-bottom:8px;"),
            p(filename)
              .withStyle("text-align:center;font-family:monospace;font-size:13px;color:#888;margin-top:0;margin-bottom:24px;"),

            saved && warnings.isEmpty()
              ? div("Saved successfully.")
                  .withStyle("padding:10px 14px;margin-bottom:20px;border-radius:8px;" +
                             "background:#e8f5e9;border:1px solid #a5d6a7;color:#2e7d32;font-size:14px;")
              : null,

            !warnings.isEmpty()
              ? div().withStyle(
                  "padding:12px 14px;margin-bottom:20px;border-radius:8px;" +
                  "background:#fff8e1;border:1px solid #ffe082;color:#7c5700;font-size:14px;")
                  .with(
                    b(saved ? "Saved — with warnings:" : "Warnings:"),
                    ul().withStyle("margin:8px 0 0 0;padding-left:20px;").with(
                      each(warnings, w -> li(w))
                    )
                  )
              : null,

            p("admin.token and webauthn.signcount.check take effect immediately; " +
              "all other settings require a service restart.")
              .withStyle("font-size:12px;color:#888;margin:0 0 8px 0;"),

            form().withMethod("post").withAction(postUrl).with(
              textarea().withName("content")
                .attr("rows", "24")
                .withStyle(
                  FIELD +
                  "width:calc(100% - 22px);display:block;" +
                  "font-family:monospace;font-size:13px;resize:vertical;")
                .withText(content),
              div().withStyle("text-align:center;margin-top:16px;").with(
                button("Save").withType("submit")
                  .withStyle("padding:10px 28px;font-size:14px;cursor:pointer;")
              )
            )
          ),
          AdminNav.footer()
        )
    ));
  }
}
